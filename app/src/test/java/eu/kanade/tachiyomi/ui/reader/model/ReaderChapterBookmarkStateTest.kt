package eu.kanade.tachiyomi.ui.reader.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

class ReaderChapterBookmarkStateTest {

    @Test
    fun `cached Reader chapter accepts persisted bookmark color updates`() {
        val initial = Chapter.create().copy(
            id = 1,
            mangaId = 42,
            bookmark = true,
            bookmarkColor = BookmarkColor.ORANGE,
        )

        val readerChapter = ReaderChapter(initial)

        readerChapter.syncBookmarkState(
            initial.copy(
                bookmarkColor = BookmarkColor.BLUE,
            ),
        )

        readerChapter.chapter.bookmarkColor shouldBe BookmarkColor.BLUE
        readerChapter.chapter.bookmark shouldBe true
    }
}
