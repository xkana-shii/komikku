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
    ) {
        val dbCategoriesByName = getCategories.await().associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val entries = backupMangas.map { backupManga ->
            var manga = backupManga.getMangaImpl()
            // SY -->
            manga = EXHMigrations.migrateBackupEntry(manga)
            // SY <--
            RestoredManga(
                manga = manga,
                chapters = backupManga.chapters.map { it.toChapterImpl() },
                categoryIds = backupManga.categories.mapNotNull { backupCategoryOrder ->
                    backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                        dbCategoriesByName[backupCategory.name]?.id
                    }
                },
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

        restoreRepository.restoreManga(entries, isSync) { manga ->
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
