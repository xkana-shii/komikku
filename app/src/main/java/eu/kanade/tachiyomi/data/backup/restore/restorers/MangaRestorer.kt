package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Clock

@Inject
class MangaRestorer(
    private val restoreRepository: RestoreRepository,
    private val getCategories: GetCategories,
    private val fetchInterval: FetchInterval,
) {
    var isSync: Boolean = false
    // KUN -->
    /** Epoch seconds of the upload snapshot; local chapters older than it absent from the response are pruned. */
    var removeMissingChaptersBefore: Long? = null
    // KUN <--
    private val timeZone = TimeZone.currentSystemDefault()
    private val now = Clock.System.now().toLocalDateTime(timeZone)
    private val currentFetchWindow = fetchInterval.getWindow(now.date, timeZone)

    /**
     * Returns whether an entry was already in the database at the time of this call.
     */
    suspend fun getExistingMatcher(): (BackupManga) -> Boolean {
        val urlsBySource = restoreRepository.getMangaUrlsBySourceId()

        return { it.url in urlsBySource[it.source].orEmpty() }
    }

    /**
     * Whether an entry has to be restored after the others. A merged entry resolves its references
     * against entries that must already be in the database, so it cannot go first.
     */
    fun isDeferred(backupManga: BackupManga): Boolean = backupManga.source == MERGED_SOURCE_ID

    /**
     * Restores [backupMangas] all together, so either every one of them is restored or none is.
     */
    suspend fun restore(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
        // KUN -->
        restoreCategories: Boolean = true,
        restoreTracking: Boolean = true,
        restoreHistory: Boolean = true,
        restoreCustomInfo: Boolean = true,
        // KUN <--
    ) {
        // KUN -->
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name.trim().lowercase() }
        val dbCategoriesByUid = dbCategories.filter { it.uid != 0L }.associateBy { it.uid }
        val dbCategoriesByOrder = dbCategories.associateBy { it.order }
        val dbCategoriesById = dbCategories.associateBy { it.id }
        // KUN <--
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }
        // KUN -->
        val backupCategoriesList = backupCategories.sortedBy { it.order }
        // KUN <--

        val entries = backupMangas.map { backupManga ->
            var manga = backupManga.getMangaImpl()
            // SY -->
            manga = EXHMigrations.migrateBackupEntry(manga)
            // SY <--
            RestoredManga(
                manga = manga,
                chapters = backupManga.chapters.map { it.toChapterImpl() },
                // KUN -->
                categoryIds = if (restoreCategories) {
                    val resolved = backupManga.categories.mapNotNull { backupCategoryOrder ->
                        val backupCat = backupCategoriesByOrder[backupCategoryOrder]
                            ?: backupCategoriesList.getOrNull(backupCategoryOrder.toInt())
                        if (backupCat != null) {
                            (if (backupCat.uid != 0L) dbCategoriesByUid[backupCat.uid]?.id else null)
                                ?: dbCategoriesByName[backupCat.name.trim().lowercase()]?.id
                                ?: dbCategoriesByOrder[backupCat.order]?.id
                        } else {
                            dbCategoriesByOrder[backupCategoryOrder]?.id
                                ?: dbCategoriesById[backupCategoryOrder]?.id
                        }
                    }
                    if (isSync && backupManga.categories.isNotEmpty() && resolved.isEmpty()) {
                        // Backup had categories, but none could be matched locally (e.g. order shift or UID drift).
                        // Preserve local categories rather than wiping them.
                        null
                    } else {
                        resolved
                    }
                } else {
                    emptyList()
                },
                // KUN <--
                history = backupManga.history.map {
                    val history = it.getHistoryImpl()
                    RestoredHistory(it.url, history.readAt, history.readDuration)
                },
                tracks = backupManga.tracking.map { it.getTrackImpl() },
                excludedScanlators = backupManga.excludedScanlators,
                // SY -->
                mergedMangaReferences = backupManga.mergedMangaReferences.map {
                    EXHMigrations.migrateBackupMergedMangaReference(it).getMergedMangaReference()
                },
                flatMetadata = backupManga.flatMetadata?.getFlatMetadata(mangaId = 0L),
                customMangaInfo = backupManga.getCustomMangaInfo(),
                // SY <--
            )
        }

        // KUN -->
        restoreRepository.restoreManga(
            entries,
            isSync,
            restoreCategories,
            restoreTracking,
            restoreHistory,
            restoreCustomInfo,
            removeMissingChaptersBefore,
        ) { manga ->
            // KUN <--
            val update = fetchInterval.withFetchInterval(manga, now, timeZone, currentFetchWindow)
            // A next update still ahead was worked out with the chapters the backup came from
            val preservedNextUpdate = manga.nextUpdate
                ?.takeIf { it >= currentFetchWindow.start }
            if (preservedNextUpdate != null) update.apply { nextUpdate = preservedNextUpdate } else update
        }
    }

    // KMK/SY -->
    suspend fun updateManga(manga: Manga) {
        restoreRepository.updateManga(manga, isSync)
    }
    // KMK/SY <--

    // SY -->
    private fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                thumbnailUrl = customThumbnailUrl,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--
}
