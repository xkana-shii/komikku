package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class LibraryBadgesTest {

    private fun libraryManga(
        chapterFlags: Long = 0L,
    ) = LibraryManga(
        manga = Manga.create().copy(
            id = 1L,
        ),
        categories = emptyList(),
        totalChapters = 8L,
        readCount = 3L,
        bookmarkCount = 2L,
        bookmarkReadCount = 1L,
        chapterFlags = chapterFlags,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    )

    private fun item(
        libraryManga: LibraryManga = libraryManga(),
        downloadCount: Int = 5,
        unreadCount: Long = 5L,
        isLocal: Boolean = false,
        showDownloads: Boolean = true,
        showUnread: Boolean = true,
        showLocal: Boolean = true,
        showLanguage: Boolean = true,
    ) = LibraryItem(
        libraryManga = libraryManga,
        downloadCount = downloadCount,
        unreadCount = unreadCount,
        isLocal = isLocal,
        sourceName = "Test source",
        sourceLanguage = "en",
        useLangIcon = false,
        badges = LibraryItem.Badges(
            downloadCount = if (showDownloads) {
                downloadCount
            } else {
                0
            },
            unreadCount = if (showUnread) {
                unreadCount
            } else {
                0L
            },
            isLocal = isLocal && showLocal,
            sourceLanguage = if (showLanguage) {
                "en"
            } else {
                ""
            },
        ),
    )

    @Test
    fun `badge state keeps download unread local and language independent`() {
        for (mask in 0 until 16) {
            val entry = item(
                showDownloads = mask and 1 != 0,
                showUnread = mask and 2 != 0,
                showLocal = mask and 4 != 0,
                showLanguage = mask and 8 != 0,
                isLocal = true,
            )

            entry.downloadCount shouldBe 5
            entry.unreadCount shouldBe 5L
            entry.isLocal shouldBe true
            entry.useLangIcon shouldBe false

            entry.badges.downloadCount shouldBe if (
                mask and 1 != 0
            ) {
                5
            } else {
                0
            }

            entry.badges.unreadCount shouldBe if (
                mask and 2 != 0
            ) {
                5L
            } else {
                0L
            }

            entry.badges.isLocal shouldBe (
                mask and 4 != 0
                )

            entry.badges.sourceLanguage shouldBe if (
                mask and 8 != 0
            ) {
                "en"
            } else {
                ""
            }
        }
    }

    @Test
    fun `ordinary unread count respects read chapters`() {
        val manga = libraryManga()

        manga.unreadCount shouldBe 5L
        manga.hasBookmarks shouldBe true
        manga.hasStarted shouldBe true
    }

    @Test
    fun `bookmarked chapter filter uses bookmarked unread count`() {
        val manga = libraryManga(
            Manga.CHAPTER_SHOW_BOOKMARKED,
        )

        manga.unreadCount shouldBe 1L
    }

    @Test
    fun `non bookmarked chapter filter excludes read chapters in that group`() {
        val manga = libraryManga(
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
        )

        manga.unreadCount shouldBe 4L
    }

    @Test
    fun `copying a library item preserves its source and presentation preferences`() {
        val original = item()
        val local = original.copy(
            isLocal = true,
            downloadCount = 0,
            badges = original.badges.copy(
                downloadCount = 0,
                isLocal = true,
            ),
        )

        local.libraryManga shouldBe original.libraryManga
        local.source shouldBe original.source
        local.sourceLanguage shouldBe "en"
        local.useLangIcon shouldBe false
        local.downloadCount shouldBe 0
        local.badges.isLocal shouldBe true
    }
}
