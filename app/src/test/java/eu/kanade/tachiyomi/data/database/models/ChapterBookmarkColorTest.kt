package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.toBackupChapter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

class ChapterBookmarkColorTest {

    @Test
    fun `backup round trip preserves every bookmark color`() {
        BookmarkColor.entries.forEach { color ->
            val chapter = Chapter.create().copy(
                id = color.value.toLong() + 1L,
                mangaId = 42L,
                url = "/chapter/${color.value}",
                name = "Chapter ${color.value}",
                bookmark = color != BookmarkColor.NONE,
                bookmarkColor = color,
            )

            val backup = chapter.toBackupChapter()
            val restored = backup.toChapterImpl()

            backup.bookmarkColor shouldBe color.value
            restored.bookmarkColor shouldBe color
            restored.bookmark shouldBe chapter.bookmark
            restored.url shouldBe chapter.url
            restored.name shouldBe chapter.name
        }
    }

    @Test
    fun `older backups default to uncolored bookmarks`() {
        val backup = BackupChapter(
            url = "/old-chapter",
            name = "Old chapter",
            bookmark = true,
        )

        backup.bookmarkColor shouldBe BookmarkColor.NONE.value
        backup.toChapterImpl().bookmark shouldBe true
        backup.toChapterImpl().bookmarkColor shouldBe BookmarkColor.NONE
    }

    @Test
    fun `unknown bookmark colors safely fall back to none`() {
        val backup = BackupChapter(
            url = "/chapter",
            bookmark = true,
            bookmarkColor = 999,
        )

        backup.toChapterImpl().bookmarkColor shouldBe BookmarkColor.NONE
        backup.toChapterImpl().bookmark shouldBe true
    }
}
