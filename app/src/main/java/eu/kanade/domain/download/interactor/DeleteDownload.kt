package eu.kanade.domain.download.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@Inject
class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) {
        // KUN -->
        // Deleting touches the download cache and disk; a cancelled caller used to abort midway and leave
        // them out of sync, so run it to completion even if the caller's job is cancelled
        // KUN <--
        // SY -->
        withNonCancellableContext {
            sourceManager.get(manga.source)?.let { source ->
                downloadManager.deleteChapters(chapters.toList(), manga, source)
            }
        }
        // SY <--
    }
}
