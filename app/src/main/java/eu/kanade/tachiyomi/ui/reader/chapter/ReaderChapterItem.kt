package eu.kanade.tachiyomi.ui.reader.chapter

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

data class ReaderChapterItem(
    val chapter: Chapter,
    val manga: Manga,
    val isCurrent: Boolean,
)
