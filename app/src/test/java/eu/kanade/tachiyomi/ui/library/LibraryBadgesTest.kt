package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.applyFilter

// KMK -->
class LibraryBadgesTest {
    @Test
    fun `badge combinations preserve downloaded unread local and fillermark filtering`() {
        val manga = mockk<LibraryManga> {
            every { id } returns 1
            every { unreadCount } returns 3
            every { hasFillermarks } returns true
        }
        // A merged manga uses the aggregate count calculated by the ScreenModel.
        val downloaded = LibraryItem(manga, downloadCount = 5, unreadCount = 3, sourceManager = mockk(), source = mockk())
        val local = downloaded.copy(downloadCount = 0, isLocal = true)
        val unavailable = downloaded.copy(downloadCount = 0)
        for (mask in 0 until 16) {
            val items = listOf(downloaded, local, unavailable).map {
                it.copy(badges = libraryBadges(it.downloadCount, it.unreadCount, it.isLocal, mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0, "en"))
            }
            items.map { item -> applyFilter(TriState.ENABLED_IS) { item.isDownloaded } } shouldBe listOf(true, true, false)
            items.map { item -> applyFilter(TriState.ENABLED_IS) { item.libraryManga.unreadCount > 0 } } shouldBe listOf(true, true, true)
            items.map { item -> applyFilter(TriState.ENABLED_IS) { item.libraryManga.hasFillermarks } } shouldBe listOf(true, true, true)
            items.first().downloadCount shouldBe 5
            items.first().unreadCount shouldBe 3
            items[1].isLocal shouldBe true
            items.first().badges.downloadCount shouldBe if (mask and 1 != 0) 5 else 0
            items.first().badges.unreadCount shouldBe if (mask and 2 != 0) 3 else 0
            items[1].badges.isLocal shouldBe (mask and 4 != 0)
            items.first().badges.sourceLanguage shouldBe if (mask and 8 != 0) "en" else ""
            items.first().source shouldBe downloaded.source
            items.first().useLangIcon shouldBe downloaded.useLangIcon
        }
    }
}
// KMK <--
