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
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.CustomMangaInfo
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
    private val getCustomMangaInfo: GetCustomMangaInfo,
    private val getFlatMetadataById: GetFlatMetadataById,
    // SY <--
) {

    operator fun invoke(
        mangas: List<Manga>,
        options: BackupOptions,
    ): Flow<BackupManga> = flow {
        mangas.chunked(CHUNK_SIZE).forEach { chunk ->
            backupMangas(chunk, options).forEach { backup ->
                emit(backup)
            }
        }
    }

    private suspend fun backupMangas(
        mangas: List<Manga>,
        options: BackupOptions,
    ): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val excludedScanlatorsByMangaId =
            mangaRepository.getExcludedScanlatorsByMangaIds(mangaIds)

        val chaptersByMangaId = if (
            options.chapters || options.history
        ) {
            chapterRepository
                .getChaptersByMangaIds(mangaIds)
                .groupBy { it.mangaId }
        } else {
            emptyMap()
        }

        val categoriesByMangaId = if (options.categories) {
            categoryRepository.getCategoriesByMangaIds(mangaIds)
        } else {
            emptyMap()
        }

        val tracksByMangaId = if (options.tracking) {
            trackRepository
                .getTracksByMangaIds(mangaIds)
                .groupBy { it.mangaId }
        } else {
            emptyMap()
        }

        val historyByMangaId = if (options.history) {
            historyRepository.getHistoryByMangaIds(mangaIds)
        } else {
            emptyMap()
        }

        return mangas.map { manga ->
            val customInfo = if (options.customInfo) {
                getCustomMangaInfo.get(manga.id)
            } else {
                null
            }

            val backup = manga.toBackupManga(customInfo)
            val chapters = chaptersByMangaId[manga.id].orEmpty()

            // SY -->
            if (manga.source == MERGED_SOURCE_ID) {
                backup.mergedMangaReferences = mangaMergeRepository
                    .getReferencesById(manga.id)
                    .map { it.toBackupMergedMangaReference() }
            }

            val source = sourceManager
                .get(manga.source)
                ?.getMainSource<MetadataSource<*, *>>()

            if (source != null) {
                getFlatMetadataById.await(manga.id)
                    ?.let { flatMetadata ->
                        backup.flatMetadata =
                            BackupFlatMetadata.copyFrom(flatMetadata)
                    }
            }
            // SY <--

            backup.excludedScanlators =
                excludedScanlatorsByMangaId[manga.id].orEmpty()

            if (options.chapters) {
                backup.chapters = chapters.map {
                    it.toBackupChapter()
                }
            }

            if (options.categories) {
                backup.categories = categoriesByMangaId[manga.id]
                    .orEmpty()
                    .map { it.order }
            }

            if (options.tracking) {
                backup.tracking = tracksByMangaId[manga.id]
                    .orEmpty()
                    .map { it.toBackupTracking() }
            }

            if (options.history) {
                val chapterUrlsById = chapters.associate {
                    it.id to it.url
                }

                backup.history = historyByMangaId[manga.id]
                    .orEmpty()
                    .mapNotNull { history ->
                        val url = chapterUrlsById[history.chapterId]
                            ?: return@mapNotNull null

                        BackupHistory(
                            url,
                            history.readAt.toEpochMillisOrZero(),
                            history.readDuration,
                        )
                    }
            }

            backup
        }
    }

    private companion object {
        const val CHUNK_SIZE = 100
    }
}

private fun Manga.toBackupManga(
    customMangaInfo: CustomMangaInfo?,
) = BackupManga(
    url = url,

    // SY -->
    title = ogTitle,
    artist = ogArtist,
    author = ogAuthor,
    description = ogDescription,
    genre = ogGenre.orEmpty(),
    status = ogStatus.toInt(),
    thumbnailUrl = ogThumbnailUrl,
    // SY <--

    favorite = favorite,
    source = source,
    dateAdded = dateAdded.toEpochMillisOrZero(),
    viewer = viewerFlags.toInt() and ReadingMode.MASK,
    viewer_flags = viewerFlags.toInt(),
    chapterFlags = chapterFlags.toInt(),
    updateStrategy = updateStrategy,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    notes = notes,
    initialized = initialized,
    memo = memo.toByteArray(),
    nextUpdate = nextUpdate.toEpochMillisOrZero(),

    // KMK --> Back up every user-owned chapter setting.
    scanlatorPriority = scanlatorPriority,
    blacklistedChapters = blacklistedChapters,
    scanlatorRangeRules = scanlatorRangeRules,
    // KMK <--
).also { backup ->
    // SY -->
    customMangaInfo?.let {
        backup.customTitle = it.title
        backup.customArtist = it.artist
        backup.customAuthor = it.author
        backup.customThumbnailUrl = it.thumbnailUrl
        backup.customDescription = it.description
        backup.customGenre = it.genre
        backup.customStatus = it.status?.toInt() ?: 0
    }
    // SY <--
}
