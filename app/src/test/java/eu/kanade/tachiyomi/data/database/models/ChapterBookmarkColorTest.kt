package eu.kanade.tachiyomi.data.database.models

import eu.kanade.domain.chapter.model.toDbChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

class ChapterBookmarkColorTest {

    @Test
    fun `Reader legacy chapter conversion preserves every bookmark color`() {
        BookmarkColor.entries.forEach { color ->
            val chapter = Chapter.create().copy(
                id = color.value.toLong() + 1,
                mangaId = 42,
                bookmark = color != BookmarkColor.NONE,
                bookmarkColor = color,
            )

            chapter.toDbChapter().toDomainChapter()!!.bookmarkColor shouldBe color
        }
    }
}
