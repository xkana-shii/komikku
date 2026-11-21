package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupDeletedItem
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.category.interactor.PruneCategoryReferences
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.sync.repository.SyncRepository

/**
 * Applies the explicit tombstone list a SyncYomi v2 delta carries in `Backup.deletions` (field
 * `11000`). Keys are the server's merge keys, so they must stay byte for byte compatible with
 * [mangaTombstoneKey] and [chapterTombstoneKey] above.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1685).
 */

private const val LOG_TAG = "SyncDeletions"
private const val MANGA_KEY_SEPARATOR = '|'
private const val CHAPTER_KEY_SEPARATOR = '\u001f'
private const val CATEGORY_UID_PREFIX = "uid:"
private const val CATEGORY_NAME_PREFIX = "name:"

/** Sync item kinds this client deletes locally. */
internal const val TOMBSTONE_MANGA = "manga"
internal const val TOMBSTONE_CHAPTER = "chapter"
internal const val TOMBSTONE_HISTORY = "history"
internal const val TOMBSTONE_CATEGORY = "category"

/**
 * Kinds a delta may tombstone that this client never applies: every one of them names a settings
 * section, and dropping a local setting because the server tombstoned a section key would invent
 * settings-deletion behaviour. They are reported instead of applied.
 */
internal val unappliedTombstoneKinds = setOf(
    "source",
    "app_pref",
    "source_pref",
    "ext_store",
    "saved_search",
    "extra",
)

/** A chapter or history tombstone: `<sourceId>|<mangaUrl>\u001f<chapterUrl>`. */
internal data class ChapterTombstone(
    val sourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
)

/** A category tombstone: `uid:<uid>` or `name:<name>`, whichever identity the deleting device knew. */
internal data class CategoryTombstone(
    val uid: Long? = null,
    val name: String? = null,
)

/** The local deletions [deletionPlan] derived from a delta, one entry per row the server tombstoned. */
internal class DeletionPlan(
    val manga: List<Pair<Long, String>>,
    val chapters: List<ChapterTombstone>,
    val history: List<ChapterTombstone>,
    val categories: List<CategoryTombstone>,
    val skippedKinds: Set<String>,
)

/** `sourceId|mangaUrl`, or null when the key is not one this client understands. */
internal fun mangaKeyOf(key: String): Pair<Long, String>? {
    val separator = key.indexOf(MANGA_KEY_SEPARATOR)
    if (separator <= 0 || separator == key.lastIndex) return null
    val sourceId = key.substring(0, separator).toLongOrNull() ?: return null
    return sourceId to key.substring(separator + 1)
}

/** `<sourceId>|<mangaUrl>\u001f<chapterUrl>`, or null when the key is not one this client understands. */
internal fun chapterKeyOf(key: String): ChapterTombstone? {
    val separator = key.indexOf(CHAPTER_KEY_SEPARATOR)
    if (separator < 0 || separator == key.lastIndex) return null
    val (sourceId, mangaUrl) = mangaKeyOf(key.substring(0, separator)) ?: return null
    return ChapterTombstone(sourceId, mangaUrl, key.substring(separator + 1))
}

/** `uid:<uid>` or `name:<name>`, or null when the key is not one this client understands. */
internal fun categoryKeyOf(key: String): CategoryTombstone? = when {
    key.startsWith(CATEGORY_UID_PREFIX) ->
        key.removePrefix(CATEGORY_UID_PREFIX).toLongOrNull()?.let { CategoryTombstone(uid = it) }

    key.startsWith(CATEGORY_NAME_PREFIX) ->
        key.removePrefix(CATEGORY_NAME_PREFIX).takeIf { it.isNotEmpty() }?.let { CategoryTombstone(name = it) }

    else -> null
}

/**
 * Splits a delta's tombstones into the deletions this client can apply. A key of a known kind that
 * cannot be read is dropped with a warning; a kind this client does not apply is reported in
 * [DeletionPlan.skippedKinds].
 */
internal fun deletionPlan(deletions: List<BackupDeletedItem>): DeletionPlan {
    val manga = mutableListOf<Pair<Long, String>>()
    val chapters = mutableListOf<ChapterTombstone>()
    val history = mutableListOf<ChapterTombstone>()
    val categories = mutableListOf<CategoryTombstone>()
    val skipped = mutableSetOf<String>()

    deletions.forEach { item ->
        when (item.kind) {
            TOMBSTONE_MANGA -> mangaKeyOf(item.key)?.let { manga += it } ?: unreadableKey(item)
            TOMBSTONE_CHAPTER -> chapterKeyOf(item.key)?.let { chapters += it } ?: unreadableKey(item)
            TOMBSTONE_HISTORY -> chapterKeyOf(item.key)?.let { history += it } ?: unreadableKey(item)
            TOMBSTONE_CATEGORY -> categoryKeyOf(item.key)?.let { categories += it } ?: unreadableKey(item)
            else -> {
                skipped += item.kind
                if (item.kind !in unappliedTombstoneKinds) {
                    logcat(LOG_TAG, LogPriority.WARN) { "Unknown tombstone kind: ${item.kind}" }
                }
            }
        }
    }

    return DeletionPlan(manga, chapters, history, categories, skipped)
}

private fun unreadableKey(item: BackupDeletedItem) {
    logcat(LOG_TAG, LogPriority.WARN) { "Ignoring ${item.kind} tombstone with an unreadable key: ${item.key}" }
}

/**
 * Deletes the local rows [plan] names: a manga takes its chapters, history and category links with
 * it (the foreign keys cascade), a chapter is a single row, a history entry is the row keyed by its
 * chapter, and a category is matched by uid or name. A key with no local row is a no-op, so a
 * tombstone of something this device never had is free.
 *
 * A history tombstone goes through [HistoryRepository.resetHistory], komi-kun's established
 * removal path for a history entry (the same one `RemoveHistory` uses): the row keeps its id but
 * loses its read date, which is what the History tab shows as gone.
 *
 * [librarySyncEnabled], [historySyncEnabled], [chaptersEnabled] and [categoriesEnabled] honour the
 * same user settings the snapshot fallback honours: a section the user disabled is not deleted on
 * the server's word.
 *
 * A chapter row is the parent of `history.chapter_id` (`ON DELETE CASCADE`), so when history sync
 * is off a tombstoned chapter that still carries a local history entry is kept: deleting it would
 * wipe history the user never asked to sync.
 *
 * [deleteManga] owns the local removal of a manga row, so the caller can drop the downloads and
 * queued chapters of it before the row (and with it every handle on them) is gone. Returns the
 * kinds that were not applied.
 */
internal suspend fun applyDeletions(
    plan: DeletionPlan,
    syncRepository: SyncRepository,
    chapterRepository: ChapterRepository,
    historyRepository: HistoryRepository,
    categoryRepository: CategoryRepository,
    pruneCategoryReferences: PruneCategoryReferences,
    deleteManga: suspend (Manga) -> Unit = { syncRepository.deleteManga(it.id) },
    librarySyncEnabled: Boolean = true,
    historySyncEnabled: Boolean = true,
    chaptersEnabled: Boolean = true,
    categoriesEnabled: Boolean = true,
): Set<String> {
    val skipped = plan.skippedKinds.toMutableSet()

    // One library read serves every manga-keyed tombstone; nothing else below touches these rows.
    val needsLocalManga = plan.manga.isNotEmpty() || plan.chapters.isNotEmpty() || plan.history.isNotEmpty()
    val localMangaByKey = if (needsLocalManga) {
        syncRepository.getAllManga().associateBy { it.source to it.url }
    } else {
        emptyMap()
    }

    if (librarySyncEnabled) {
        plan.manga.forEach { (sourceId, mangaUrl) ->
            val manga = localMangaByKey[sourceId to mangaUrl] ?: return@forEach
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${manga.url}: the delta tombstoned it" }
            deleteManga(manga)
        }
    } else if (plan.manga.isNotEmpty()) {
        skipped += TOMBSTONE_MANGA
    }

    // Chapter and history tombstones both name a chapter, so one chapter read serves both.
    val chapterRowByKey = resolveChapterRows(
        keys = plan.chapters + plan.history,
        localMangaByKey = localMangaByKey,
        chapterRepository = chapterRepository,
    )
    val chapterRows = plan.chapters.distinct().mapNotNull { chapterRowByKey[it] }
    val historyChapterRows = plan.history.distinct().mapNotNull { chapterRowByKey[it] }

    // One history read serves the reset below and the chapter gate: `history.chapter_id` cascades
    // with its chapter row, so a chapter that has one cannot be deleted while history sync is off.
    val historyIdByChapterId = if (
        chapterRowByKey.isNotEmpty() && (historySyncEnabled || plan.chapters.isNotEmpty())
    ) {
        historyRepository.getHistoryByMangaIds(chapterRowByKey.values.map { it.mangaId }.distinct())
            .values
            .flatten()
            .associate { it.chapterId to it.id }
    } else {
        emptyMap()
    }

    if (chaptersEnabled) {
        val (deletable, keptForHistory) = chapterRows.partition {
            historySyncEnabled || it.id !in historyIdByChapterId
        }
        if (deletable.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${deletable.size} chapters the delta tombstoned" }
            chapterRepository.removeChaptersWithIds(deletable.map { it.id }.distinct())
        }
        if (keptForHistory.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) {
                "Keeping ${keptForHistory.size} tombstoned chapters: they still carry local history"
            }
            skipped += TOMBSTONE_CHAPTER
        }
    } else if (plan.chapters.isNotEmpty()) {
        skipped += TOMBSTONE_CHAPTER
    }

    if (historySyncEnabled) {
        val historyIds = historyChapterRows.mapNotNull { historyIdByChapterId[it.id] }.distinct()
        if (historyIds.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) {
                "Deleting ${historyIds.size} history entries the delta tombstoned"
            }
            historyRepository.resetHistory(historyIds)
        }
    } else if (plan.history.isNotEmpty()) {
        skipped += TOMBSTONE_HISTORY
    }

    if (categoriesEnabled && plan.categories.isNotEmpty()) {
        // one read for the whole list; local ids and uids only agree by accident, so match on both identities
        val localCategories = categoryRepository.getAll().filterNot { it.id == 0L }
        val categoryIds = plan.categories.mapNotNull { tombstone ->
            localCategories.firstOrNull { category ->
                when {
                    tombstone.uid != null -> category.uid == tombstone.uid
                    tombstone.name != null -> category.name == tombstone.name
                    else -> false
                }
            }
        }.map { it.id }.distinct()
        if (categoryIds.isNotEmpty()) {
            logcat(LOG_TAG, LogPriority.DEBUG) { "Deleting ${categoryIds.size} categories the delta tombstoned" }
            categoryRepository.delete(categoryIds)
            // These rows were deleted without going through DeleteCategory, so the stale ids have to
            // leave the library, download and History filters too, or a filter keeps matching a
            // category that no longer exists.
            pruneCategoryReferences.prune(categoryIds)
        }
    } else if (plan.categories.isNotEmpty()) {
        skipped += TOMBSTONE_CATEGORY
    }

    return skipped
}

/**
 * The local chapter rows [keys] name, resolved in one pass: every manga the keys touch is read with
 * a single chapter query and matched on `remote_url`, instead of one lookup per tombstone. A key
 * whose manga or chapter this device never had is absent from the result, as is a key whose manga
 * a tombstone above already deleted.
 */
private suspend fun resolveChapterRows(
    keys: List<ChapterTombstone>,
    localMangaByKey: Map<Pair<Long, String>, Manga>,
    chapterRepository: ChapterRepository,
): Map<ChapterTombstone, Chapter> {
    if (keys.isEmpty()) return emptyMap()
    val localMangaIdByKey = keys.distinct()
        .associateWith { localMangaByKey[it.sourceId to it.mangaUrl]?.id }
    val mangaIds = localMangaIdByKey.values.filterNotNull().distinct()
    if (mangaIds.isEmpty()) return emptyMap()
    val chapterByMangaAndUrl = chapterRepository.getChaptersByMangaIds(mangaIds)
        .associateBy { it.mangaId to it.url }
    return localMangaIdByKey.mapNotNull { (key, mangaId) ->
        mangaId?.let { chapterByMangaAndUrl[it to key.chapterUrl] }?.let { key to it }
    }.toMap()
}

/**
 * Drops from the local side of a category merge the categories a tombstone removed. The merge is
 * built from the upload snapshot taken before the deletions ran, so without this it hands the
 * just-deleted rows to the restore, which re-inserts them in the same run.
 */
internal fun withoutTombstonedCategories(
    local: List<BackupCategory>,
    tombstones: List<CategoryTombstone>,
): List<BackupCategory> {
    if (tombstones.isEmpty()) return local
    return local.filterNot { category ->
        tombstones.any { tombstone ->
            when {
                tombstone.uid != null -> category.uid == tombstone.uid
                tombstone.name != null -> category.name == tombstone.name
                else -> false
            }
        }
    }
}

/**
 * Whether a response carries nothing this client has to apply: the server echoed our own backup, or
 * a v2 delta reports no change and lists no tombstone. A delta whose only change is a deletion
 * reports `changed = false` and would otherwise be skipped, leaving the tombstone unapplied.
 */
internal fun skipRemoteResponse(
    ownBackup: Boolean,
    protocolV2: Boolean,
    changed: Boolean,
    deletions: List<BackupDeletedItem>,
): Boolean = ownBackup || (protocolV2 && !changed && deletions.isEmpty())
