package eu.kanade.tachiyomi.ui.reader.chapter

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.time.format.DateTimeFormatter

data class ReaderChapterItem(
    val chapter: Chapter,
    val manga: Manga,
    val isCurrent: Boolean,
    val dateFormat: DateTimeFormatter,
    val downloadState: eu.kanade.tachiyomi.data.download.model.Download.State = eu.kanade.tachiyomi.data.download.model.Download.State.NOT_DOWNLOADED,
    val downloadProgress: Int = 0,
)
