package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import mihon.domain.library.model.search.LibraryField
import mihon.domain.library.model.search.LibraryFieldQueryNode
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

// KNS -->
class LibrarySearchFieldsTest {

    private val categories = listOf(
        cat(1, "Shounen"),
        cat(2, "Reading", parent = 1),
        cat(4, "Josei"),
        cat(5, "Reading", parent = 4),
    )
    private val fields = LibrarySearchFields(
        categories = categories,
        defaultCategoryName = "Default",
        statusName = { if (it == 1L) "Ongoing" else null },
        trackerValues = { track -> if (track.trackerId == 2L) listOf("AniList", "Reading") else emptyList() },
    )

    @Test
    fun `category terms match the categories and their parents, not same-named siblings`() {
        val shounenReading = item(categories = listOf(2))
        fields.matches(node(LibraryField.CATEGORY, "Shounen"), shounenReading, emptyList()) shouldBe true
        fields.matches(node(LibraryField.CATEGORY, "Reading"), shounenReading, emptyList()) shouldBe true
        fields.matches(node(LibraryField.CATEGORY, "Josei"), shounenReading, emptyList()) shouldBe false
        fields.matches(node(LibraryField.CATEGORY, "5"), shounenReading, emptyList()) shouldBe false
        fields.matches(node(LibraryField.CATEGORY, "2"), shounenReading, emptyList()) shouldBe true
    }

    @Test
    fun `an entry without categories is in the default category`() {
        val uncategorized = item(categories = listOf(0))
        fields.matches(node(LibraryField.CATEGORY, "default"), uncategorized, emptyList()) shouldBe true
        fields.matches(node(LibraryField.CATEGORY, "0"), uncategorized, emptyList()) shouldBe true
        fields.matches(node(LibraryField.CATEGORY, "Shounen"), uncategorized, emptyList()) shouldBe false
    }

    @Test
    fun `tracker and status terms use the entry's tracks and publishing status`() {
        val entry = item(categories = listOf(1), status = 1)
        val tracks = listOf(track(trackerId = 2))
        fields.matches(node(LibraryField.TRACKER, "anilist"), entry, tracks) shouldBe true
        fields.matches(node(LibraryField.TRACKER, "reading"), entry, tracks) shouldBe true
        fields.matches(node(LibraryField.TRACKER, "anilist"), entry, emptyList()) shouldBe false
        fields.matches(node(LibraryField.TRACKER, "anilist", negated = true), entry, emptyList()) shouldBe true
        fields.matches(node(LibraryField.STATUS, "ongoing"), entry, emptyList()) shouldBe true
        fields.matches(node(LibraryField.STATUS, "1"), entry, emptyList()) shouldBe true
        fields.matches(node(LibraryField.STATUS, "2"), entry, emptyList()) shouldBe false
    }

    private fun node(field: LibraryField, value: String, negated: Boolean = false) =
        LibraryFieldQueryNode(field, value, negated)

    private fun item(categories: List<Long>, status: Long = 0) = LibraryItem(
        libraryManga = LibraryManga(
            manga = Manga.create().copy(id = 1, ogStatus = status),
            categories = categories,
            totalChapters = 0,
            readCount = 0,
            bookmarkCount = 0,
            bookmarkReadCount = 0,
            chapterFlags = 0,
            latestUpload = null,
            chapterFetchedAt = null,
            lastRead = null,
        ),
        badges = LibraryItem.Badges(downloadCount = 0, unreadCount = 0, isLocal = false, sourceLanguage = ""),
    )

    private fun track(trackerId: Long) = Track(
        id = 1, mangaId = 1, trackerId = trackerId, remoteId = 1, libraryId = null, title = "T",
        lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )

    private fun cat(id: Long, name: String, parent: Long = 0) =
        Category(id = id, name = name, order = id, flags = 0, hidden = false, parentId = parent)
}
// KNS <--
