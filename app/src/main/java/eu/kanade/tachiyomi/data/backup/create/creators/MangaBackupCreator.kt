package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.toBackupTracking
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toEpochMillisOrZero
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.repository.TrackRepository

@Inject
class MangaBackupCreator(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val historyRepository: HistoryRepository,
    private val trackRepository: TrackRepository,
    // SY -->
    private val mangaMergeRepository: MangaMergeRepository,
    private val sourceManager: SourceManager,
    private val getFlatMetadataById: GetFlatMetadataById,
    // SY <--
) {

    /**
     * Builds the entries a chunk at a time, so only one chunk's chapters are held at once.
     */
    operator fun invoke(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> = flow {
        mangas.chunked(CHUNK_SIZE).forEach { chunk ->
            backupMangas(chunk, options).forEach { emit(it) }
        }
    }

    private suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val excludedScanlatorsByMangaId = mangaRepository.getExcludedScanlatorsByMangaIds(mangaIds)
        // History is backed up by chapter URL, so it needs the chapters too
        val chaptersByMangaId = if (options.chapters || options.history) {
            // KMK: the chapter batch query predates this port and returns a flat list, so it is grouped here
            chapterRepository.getChaptersByMangaIds(mangaIds).groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val categoriesByMangaId = if (options.categories) {
            categoryRepository.getCategoriesByMangaIds(mangaIds)
        } else {
            emptyMap()
        }
        val tracksByMangaId = if (options.tracking) {
            // KMK: same as the chapters above, the track batch query returns a flat list
            trackRepository.getTracksByMangaIds(mangaIds).groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val historyByMangaId = if (options.history) historyRepository.getHistoryByMangaIds(mangaIds) else emptyMap()

        return mangas.map { manga ->
            // Entry for this manga
            val mangaObject = manga.toBackupManga(/* SY --> */options.customInfo/* SY <-- */)
            // KUN -->
            // Marks whether this entry carries the custom-info section, so a receiver can tell
            // "the user cleared every override" from "this payload never spoke about them".
            mangaObject.customInfoIncluded = options.customInfo
            // KUN <--
            val chapters = chaptersByMangaId[manga.id].orEmpty()

            // SY -->
            if (manga.source == MERGED_SOURCE_ID) {
                mangaObject.mergedMangaReferences = mangaMergeRepository
                    .getReferencesById(manga.id)
                    .map { it.toBackupMergedMangaReference() }
                // KUN -->
                // The list is complete for this entry, so a winning receiver may reconcile removals.
                mangaObject.mergedMangaReferencesIncluded = true
                // KUN <--
            }

            val source = sourceManager.get(manga.source)?.getMainSource<MetadataSource<*, *>>()
            if (source != null) {
                getFlatMetadataById.await(manga.id)?.let { flatMetadata ->
                    mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
                }
            }
            // SY <--

            mangaObject.excludedScanlators = excludedScanlatorsByMangaId[manga.id].orEmpty()

            // KNS -->
            mangaObject.scanlatorPriority = manga.scanlatorPriority
            mangaObject.blacklistedChapters = manga.blacklistedChapters
            mangaObject.scanlatorRangeRules = manga.scanlatorRangeRules
            mangaObject.scanlatorSettingsPresent = true
            mangaObject.smartScanlatorMergeOverride = when (manga.smartMergeOverride) {
                true -> "enabled"
                false -> "disabled"
                null -> "global"
            }
            // KNS <--

            if (options.chapters) {
                // Backup all the chapters
                mangaObject.chapters = chapters.map { it.toBackupChapter() }
            }

            if (options.categories) {
                // Backup categories for this manga
                mangaObject.categories = categoriesByMangaId[manga.id].orEmpty()
                    // KNS --> a selective backup only files the manga under the selected categories
                    .filter { options.includedCategoryIds?.contains(it.id) ?: true }
                    // KNS <--
                    .map { it.order }
            }

            if (options.tracking) {
                mangaObject.tracking = tracksByMangaId[manga.id].orEmpty().map { it.toBackupTracking() }
            }

            if (options.history) {
                val chapterUrlsById = chapters.associate { it.id to it.url }
                mangaObject.history = historyByMangaId[manga.id].orEmpty().mapNotNull { history ->
                    // KUN -->
                    // A chapter removed since its history was read takes that history with it;
                    // skip an orphan row instead of aborting the whole backup.
                    val url = chapterUrlsById[history.chapterId] ?: return@mapNotNull null
                    // KUN <--
                    BackupHistory(url, history.readAt.toEpochMillisOrZero(), history.readDuration)
                }
            }

            mangaObject
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
    }
}

private fun Manga.toBackupManga(/* SY --> */includeCustomInfo: Boolean/* SY <-- */) =
    BackupManga(
        url = this.url,
        // SY -->
        title = this.ogTitle,
        artist = this.ogArtist,
        author = this.ogAuthor,
        description = this.ogDescription,
        genre = this.ogGenre.orEmpty(),
        status = this.ogStatus.toInt(),
        thumbnailUrl = this.ogThumbnailUrl,
        // SY <--
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded.toEpochMillisOrZero(),
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        initialized = this.initialized,
        memo = this.memo.toByteArray(),
        nextUpdate = this.nextUpdate.toEpochMillisOrZero(),
        // SY -->
    ).also { backupManga ->
        if (includeCustomInfo) {
            backupManga.customTitle = userTitle
            backupManga.customArtist = userArtist
            backupManga.customAuthor = userAuthor
            backupManga.customThumbnailUrl = userThumbnailUrl
            backupManga.customDescription = userDescription
            backupManga.customGenre = userGenre
            backupManga.customStatus = userStatus?.toInt() ?: 0
        }
    }
// SY <--
