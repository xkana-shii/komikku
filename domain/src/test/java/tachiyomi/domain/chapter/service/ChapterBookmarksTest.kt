package tachiyomi.domain.chapter.service

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.ChapterBookmarks.Target
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Instant

// KNS -->
class ChapterBookmarksTest {

    private val plain = Target(1, bookmark = false, bookmarkColor = 0)
    private val ordinary = Target(2, bookmark = true, bookmarkColor = 0)
    private val red = Target(3, bookmark = true, bookmarkColor = 1)
    private val blue = Target(4, bookmark = true, bookmarkColor = 5)

    @Test
    fun `a normal tap adds ordinary bookmarks and removes bookmarks without a stale color`() {
        ChapterBookmarks.toggle(listOf(plain, ordinary), bookmark = true).map { it.state() } shouldBe
            listOf(Triple(1L, true, 0))
        ChapterBookmarks.toggle(listOf(red, blue, plain), bookmark = false).map { it.state() } shouldBe
            listOf(Triple(3L, false, 0), Triple(4L, false, 0))
    }

    @Test
    fun `every palette entry is saved under its id, Default is an ordinary bookmark`() {
        BookmarkColor.entries.map { color ->
            ChapterBookmarks.setColor(listOf(plain), color).single().state()
        } shouldBe (0..6).map { Triple(1L, true, it) }
        ChapterBookmarks.setColor(listOf(red), BookmarkColor.NONE).single().state() shouldBe Triple(3L, true, 0)
    }

    @Test
    fun `a color applies to every selected chapter, replacing colors and bookmarking plain ones`() {
        ChapterBookmarks.setColor(listOf(plain, ordinary, red, blue), BookmarkColor.GREEN).map { it.state() } shouldBe
            listOf(Triple(1L, true, 4), Triple(2L, true, 4), Triple(3L, true, 4), Triple(4L, true, 4))
    }

    @Test
    fun `chapters that already have the chosen bookmark are not rewritten`() {
        ChapterBookmarks.setColor(listOf(red), BookmarkColor.RED).shouldBeEmpty()
        ChapterBookmarks.setColor(listOf(ordinary), BookmarkColor.NONE).shouldBeEmpty()
    }

    @Test
    fun `the current color is only marked for a uniformly bookmarked selection`() {
        ChapterBookmarks.currentColor(listOf(red)) shouldBe BookmarkColor.RED
        ChapterBookmarks.currentColor(listOf(red, red.copy(chapterId = 9))) shouldBe BookmarkColor.RED
        ChapterBookmarks.currentColor(listOf(ordinary)) shouldBe BookmarkColor.NONE
        ChapterBookmarks.currentColor(listOf(red, blue)) shouldBe null
        ChapterBookmarks.currentColor(listOf(red, plain)) shouldBe null
        ChapterBookmarks.currentColor(listOf(plain)) shouldBe null
        // An unbookmarked chapter with a leftover color id is still not marked.
        ChapterBookmarks.currentColor(listOf(plain.copy(bookmarkColor = 3))) shouldBe null
    }

    @Test
    fun `the manga screen, Updates and the reader build identical updates for one chapter`() {
        val chapter = Chapter.create().copy(id = 7, bookmark = true, bookmarkColor = 2)
        val update = UpdatesWithRelations(
            mangaId = 1,
            ogMangaTitle = "",
            chapterId = 7,
            chapterName = "",
            scanlator = null,
            chapterUrl = "/7",
            read = false,
            bookmark = true,
            bookmarkColor = 2,
            lastPageRead = 0,
            sourceId = 1,
            dateFetch = Instant.fromEpochMilliseconds(0),
            coverData = MangaCover(mangaId = 1, sourceId = 1, isMangaFavorite = true, ogUrl = null, lastModified = null),
        )
        chapter.bookmarkTarget() shouldBe update.bookmarkTarget()
        ChapterBookmarks.setColor(listOf(chapter.bookmarkTarget()), BookmarkColor.PURPLE).map { it.state() } shouldBe
            ChapterBookmarks.setColor(listOf(update.bookmarkTarget()), BookmarkColor.PURPLE).map { it.state() }
        ChapterBookmarks.toggle(listOf(chapter.bookmarkTarget()), false).map { it.state() } shouldBe
            ChapterBookmarks.toggle(listOf(update.bookmarkTarget()), false).map { it.state() }
    }

    private fun ChapterUpdate.state() = Triple(id, bookmark, bookmarkColor)
}
// KNS <--
