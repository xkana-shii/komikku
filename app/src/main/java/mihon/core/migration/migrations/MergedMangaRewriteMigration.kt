package mihon.core.migration.migrations

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.source.Source
import exh.source.MERGED_SOURCE_ID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.interactor.InsertMerge
import tachiyomi.domain.manga.interactor.InsertMergedEntry
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.source.service.SourceManager

/**
 * SY-specific file
 */
class MergedMangaRewriteMigration : Migration {
    override val version: Float = 7f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val chapterRepository = migrationContext.get<ChapterRepository>() ?: return@withIOContext false
        val getMangaBySource = migrationContext.get<GetMangaBySource>() ?: return@withIOContext false
        val getManga = migrationContext.get<GetManga>() ?: return@withIOContext false
        val updateManga = migrationContext.get<UpdateManga>() ?: return@withIOContext false
        val insertMergedEntry = migrationContext.get<InsertMergedEntry>() ?: return@withIOContext false
        val insertMerge = migrationContext.get<InsertMerge>() ?: return@withIOContext false
        val sourceManager = migrationContext.get<SourceManager>() ?: return@withIOContext false
        val deleteChapters = migrationContext.get<DeleteChapters>() ?: return@withIOContext false
        val updateChapter = migrationContext.get<UpdateChapter>() ?: return@withIOContext false
        val mergedMangas = getMangaBySource.await(MERGED_SOURCE_ID)

        if (mergedMangas.isNotEmpty()) {
            val mangaConfigs = mergedMangas.mapNotNull { mergedManga ->
                readMangaConfig(mergedManga)?.let { mergedManga to it }
            }
            if (mangaConfigs.isNotEmpty()) {
                val mangaToUpdate = mutableListOf<MangaUpdate>()
                val mergedEntries = mutableListOf<MergedEntry>()
                val mergeInserts = mutableListOf<Triple<Long, Int, Long?>>()
                mangaConfigs.onEach { mergedManga ->
                    val newFirst = mergedManga.second.children.firstOrNull()?.url?.let {
                        if (getManga.await(it, MERGED_SOURCE_ID) != null) return@onEach
                        mangaToUpdate += MangaUpdate(mergedManga.first.id) { url = it }
                        mergedManga.first.copy(url = it)
                    } ?: mergedManga.first
                    val startCount = mergedEntries.size
                    var infoMangaId: Long? = null
                    mergedManga.second.children.distinct().forEach { mangaSource ->
                        val load = mangaSource.load(getManga, sourceManager) ?: return@forEach
                        if (infoMangaId == null) infoMangaId = load.manga.id
                        mergedEntries += MergedEntry(
                            id = -1,
                            mergeId = newFirst.id,
                            mangaId = load.manga.id,
                            fetchChapterUpdates = true,
                            downloadChapters = true,
                            chapterPriority = 0,
                        )
                    }
                    if (mergedEntries.size > startCount) {
                        mergeInserts += Triple(newFirst.id, 0, infoMangaId)
                    }
                }

                updateManga.awaitAll(mangaToUpdate)
                mergeInserts.forEach { (mangaId, sortMode, infoId) -> insertMerge.await(mangaId, sortMode, infoId) }
                insertMergedEntry.awaitAll(mergedEntries)

                val loadedMangaList = mangaConfigs
                    .map { it.second.children }
                    .flatten()
                    .mapNotNull { it.load(getManga, sourceManager) }
                    .distinct()
                val chapters =
                    chapterRepository
                        .getChaptersByMangaIds(mergedMangas.map { it.id })

                val mergedMangaChapters =
                    chapterRepository
                        .getChaptersByMangaIds(loadedMangaList.map { it.manga.id })

                val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter ->
                    loadedMangaList.firstOrNull {
                        it.manga.id == chapter.id
                    }?.let { it to chapter }
                }
                val parsedChapters = chapters.filter {
                    it.read || it.lastPageRead != 0
                }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                val chaptersToUpdate = mutableListOf<ChapterUpdate>()
                parsedChapters.forEach { parsedChapter ->
                    mergedMangaChaptersMatched.firstOrNull {
                        it.second.url == parsedChapter.second.url &&
                            it.first.source.id == parsedChapter.second.source &&
                            it.first.manga.url == parsedChapter.second.mangaUrl
                    }?.let {
                        chaptersToUpdate += ChapterUpdate(it.second.id) {
                            read = parsedChapter.first.read
                            lastPageRead = parsedChapter.first.lastPageRead
                        }
                    }
                }

                deleteChapters.await(mergedMangaChapters.map { it.id })
                updateChapter.awaitAll(chaptersToUpdate)
            }
        }
        return@withIOContext true
    }

    @Serializable
    private data class UrlConfig(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
        @SerialName("m")
        val mangaUrl: String,
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>,
    ) {
        companion object {
            fun readFromUrl(url: String): MangaConfig? {
                return try {
                    Json.decodeFromString(url)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun readMangaConfig(manga: Manga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
    ) {
        suspend fun load(getManga: GetManga, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = getManga.await(url, source) ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    private fun readUrlConfig(url: String): UrlConfig? {
        return try {
            Json.decodeFromString(url)
        } catch (e: Exception) {
            null
        }
    }

    private data class LoadedMangaSource(val source: Source, val manga: Manga)
}
