// KUN -->
package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.sync.chapterTombstoneKey
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Bumps the sync state of manga whose user-visible change lives outside the manga row.
 *
 * History resets write `read_at = NULL`, and a custom-info edit only touches the `user_*` columns.
 * `update_last_modified_at_mangas` fires on any UPDATE, so the delta already selects the row, but
 * `update_manga_version` does not watch `user_*`: without the bump the payload would go out with an
 * unchanged `version` and lose the server's version-first merge. The bump is what makes the edit win.
 *
 * The bump is gated on the sync service being enabled *and* on the matching "choose what to sync"
 * flag. Uploading a manga while a section is disabled would send that section's local (possibly
 * empty or stale) content and let it win by version on the server, so a disabled section must stay
 * completely untouched.
 */
@Inject
@SingleIn(AppScope::class)
class SyncStateBumper(
    private val syncPreferences: SyncPreferences,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
) {

    /** Bumps every manga whose history was reset/tombstoned by the user. */
    suspend fun bumpForHistoryRemoval(mangaIds: Collection<Long>) {
        if (mangaIds.isEmpty() || !isEnabled { it.history }) return
        mangaIds.distinct().forEach { mangaRepository.bumpSyncVersion(it) }
    }

    /**
     * Records the history entries a removal is about to reset as `history` tombstones, before the
     * reset takes the read date the server identifies the entry by. The key is the chapter tombstone
     * key: the server's history key is built from the same manga and chapter urls.
     *
     * [chapterIds] names the exact entries a selection removed; null means every history entry of
     * [mangaIds], which is what the per-manga removal resets in one SQL statement.
     */
    suspend fun rememberHistoryRemoval(
        mangaIds: Collection<Long>,
        chapterIds: Collection<Long>? = null,
    ) {
        if (mangaIds.isEmpty() || !isEnabled { it.history }) return
        val ids = mangaIds.distinct()
        val chapterIdsToForget = chapterIds?.toSet()
            ?: historyRepository.getHistoryByMangaIds(ids).values
                .flatten()
                .mapTo(mutableSetOf()) { it.chapterId }
        if (chapterIdsToForget.isEmpty()) return

        // One manga read for the whole selection, then one chapter read per selected entry: never
        // every chapter of the manga, which a long series would make thousands of rows.
        val mangaById = mangaRepository.getMangaByIds(ids).associateBy { it.id }
        val keys = chapterIdsToForget.mapNotNull { chapterId ->
            val chapter = chapterRepository.getChapterById(chapterId) ?: return@mapNotNull null
            val manga = mangaById[chapter.mangaId] ?: return@mapNotNull null
            chapterTombstoneKey(manga.source, manga.url, chapter.url)
        }
        syncPreferences.rememberDeletedHistory(keys)
    }

    /**
     * Bumps [mangaId] when a custom-info edit actually changed the stored overrides. [before] is the
     * value read from the manga row before the edit, [after] the value that was written.
     *
     * Only user edits call this: the restore path writes custom info without bumping, because a
     * restore must not echo the applied payload back as a new local edit.
     */
    suspend fun bumpForCustomInfoEdit(
        mangaId: Long,
        before: MangaUserInfoUpdate?,
        after: MangaUserInfoUpdate,
    ) {
        if (!isEnabled { it.customInfo }) return
        if (before.normalized() == after.normalized()) return
        mangaRepository.bumpSyncVersion(mangaId)
    }

    private inline fun isEnabled(section: (SyncSettings) -> Boolean): Boolean {
        if (!syncPreferences.isSyncEnabled()) return false
        return section(syncPreferences.getSyncSettings())
    }

    /**
     * Mirrors what `MangaRepository.updateUserInfo` stores: a blank title and a zero status are not
     * overrides, and a value with no override at all is the same as an unedited row. Without this,
     * clearing an override to `null`/blank would compare unequal to the stored `null` and bump the
     * manga for a no-op.
     */
    private fun MangaUserInfoUpdate?.normalized(): MangaUserInfoUpdate? {
        val info = this ?: return null
        val normalized = info.copy(
            title = info.title?.takeUnless { it.isBlank() },
            status = info.status?.takeUnless { it == 0L },
        )
        val hasOverride = normalized.title != null ||
            normalized.author != null ||
            normalized.artist != null ||
            normalized.thumbnailUrl != null ||
            normalized.description != null ||
            normalized.genre != null ||
            normalized.status != null
        return normalized.takeIf { hasOverride }
    }
}
// KUN <--
