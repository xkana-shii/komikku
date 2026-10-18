package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

class ChapterBookmarkColorTest {

    @Test
    fun `Reader chapter preserves every bookmark color`() {
        BookmarkColor.entries.forEach { color ->
            val chapter = Chapter.create().copy(
                id = color.value.toLong() + 1L,
                mangaId = 42L,
                bookmark = color != BookmarkColor.NONE,
                bookmarkColor = color,
            )

            val readerChapter = ReaderChapter(chapter)

            readerChapter.chapter.bookmarkColor shouldBe color
            readerChapter.chapter.bookmark shouldBe (
                color != BookmarkColor.NONE
                )
        }
    }

    @Test
    fun `Reader chapter synchronizes every changed bookmark color`() {
        val initial = Chapter.create().copy(
            id = 1L,
            mangaId = 42L,
            bookmark = false,
            bookmarkColor = BookmarkColor.NONE,
        )

        val readerChapter = ReaderChapter(initial)

        BookmarkColor.entries.forEach { color ->
            val bookmarked = color != BookmarkColor.NONE

            readerChapter.syncBookmarkState(
                initial.copy(
                    bookmark = bookmarked,
                    bookmarkColor = color,
                ),
            )

            readerChapter.chapter.bookmarkColor shouldBe color
            readerChapter.chapter.bookmark shouldBe bookmarked
            readerChapter.chapter.id shouldBe initial.id
            readerChapter.chapter.mangaId shouldBe initial.mangaId
        }
    }
}
