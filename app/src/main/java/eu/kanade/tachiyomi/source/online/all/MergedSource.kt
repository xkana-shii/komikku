package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.copy
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.app.di.appGraph
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergeSettings
import tachiyomi.domain.manga.interactor.GetMergedEntriesById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.source.service.SourceManager

class MergedSource(private val context: Context) : HttpSource() {
    private val getManga: GetManga get() = context.appGraph.getManga
    private val getMergedEntriesById: GetMergedEntriesById get() = context.appGraph.getMergedEntriesById
    private val getMergeSettings: GetMergeSettings get() = context.appGraph.getMergeSettings
    private val updateMangaFromRemote: UpdateMangaFromRemote get() = context.appGraph.updateMangaFromRemote
    private val sourceManager: SourceManager get() = context.appGraph.sourceManager
    private val downloadManager: DownloadManager get() = context.appGraph.downloadManager
    private val filterChaptersForDownload: FilterChaptersForDownload get() = context.appGraph.filterChaptersForDownload

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchChapterList(manga: SManga) = throw UnsupportedOperationException()
    override suspend fun getImage(page: Page, existingSize: Long): Response = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException()
    override suspend fun getImageUrl(page: Page) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter) = throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int) = throw UnsupportedOperationException()
    override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // KMK -->
        val asyncManga = if (fetchDetails) getMangaDetails(manga) else null
        return SMangaUpdate(asyncManga ?: manga, chapters)
        // KMK <--
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        return withIOContext {
            val mergedManga = requireNotNull(getManga.await(manga.url, id)) { "merged manga not in db" }
            val mangaEntries = getMergedEntriesById.await(mergedManga.id)
                .apply {
                    require(isNotEmpty()) { "Manga merged entries are empty, info unavailable, merge is likely corrupted" }
                }

            val infoMangaId = getMergeSettings.await(mergedManga.id)?.infoMangaId
            val infoEntry = mangaEntries.firstOrNull { it.mangaId == infoMangaId }
                ?: mangaEntries.first()
            val dbManga = getManga.await(infoEntry.mangaId)?.toSManga()
            (dbManga ?: mergedManga.toSManga()).copy(
                url = manga.url,
            )
        }
    }

    suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): List<Chapter> {
        val mangaEntries = getMergedEntriesById.await(manga.id)
        require(mangaEntries.isNotEmpty()) {
            "Manga merged entries are empty, chapters unavailable, merge is likely corrupted"
        }

        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            mangaEntries
                .map { entry ->
                    async {
                        semaphore.withPermit {
                            try {
                                val (source, loadedManga, _) = entry.load()
                                if (loadedManga != null && entry.fetchChapterUpdates) {
                                    // KUN -->
                                    // The fork's UpdateMangaFromRemote takes the source/manga by name and
                                    // defaults fetchDetails to false.
                                    val results = updateMangaFromRemote(
                                        source = source,
                                        manga = loadedManga,
                                        fetchChapters = true,
                                    ).getOrThrow().newChapters
                                    // KUN <--

                                    if (downloadChapters && entry.downloadChapters) {
                                        val chaptersToDownload = filterChaptersForDownload.await(manga, results)
                                        if (chaptersToDownload.isNotEmpty()) {
                                            downloadManager.downloadChapters(
                                                loadedManga,
                                                chaptersToDownload,
                                            )
                                        }
                                    }
                                    results
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                exception = e
                                emptyList()
                            }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }.also {
            exception?.let { throw it }
        }
    }

    suspend fun MergedEntry.load(): LoadedMangaSource {
        val manga = getManga.await(mangaId)
        val source = sourceManager.getOrStub(manga?.source ?: -1)
        return LoadedMangaSource(source, manga, this)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val entry: MergedEntry)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
