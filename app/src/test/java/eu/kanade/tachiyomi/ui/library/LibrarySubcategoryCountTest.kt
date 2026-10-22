package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

// KNS -->
/**
 * Library tab badges count what each tab shows: the tab's own selected subcategory (with its
 * descendants), whether or not the tab is active. Tests run against [LibraryViewModel.State].
 */
class LibrarySubcategoryCountTest {

    private val normal = cat(1, "Normal", order = 0)
    private val test = cat(2, "Test", parent = 1, order = 1)
    private val other = cat(3, "Other", parent = 1, order = 2)
    private val deep = cat(4, "Deep", parent = 2, order = 3)
    private val empty = cat(5, "Empty", parent = 1, order = 4)
    private val shounen = cat(6, "Shounen", order = 5)
    private val weekly = cat(7, "Weekly", parent = 6, order = 6)
    private val categories = listOf(normal, test, other, deep, empty, shounen, weekly)

    /**
     * Normal: 4 direct + Test subtree 2 (110 in Test, 111 in Deep and Other) + Other 5 (111, 120–123)
     * = 10 distinct. Shounen: 1 direct + Weekly 7.
     */
    private val memberships: List<Pair<Long, List<Long>>> =
        (100L..103L).map { it to listOf(1L) } +
            listOf(110L to listOf(2L), 111L to listOf(4L, 3L)) +
            (120L..123L).map { it to listOf(3L) } +
            listOf(200L to listOf(6L)) +
            (201L..207L).map { it to listOf(7L) }

    private val base = state(memberships)

    @Test
    fun `selecting a subcategory sets its parent's badge, All restores the full count`() {
        base.count(normal) shouldBe 10
        base.select(1, 2).count(normal) shouldBe 2
        base.select(1, 3).count(normal) shouldBe 5
        base.withSelection(selectSubcategory(mapOf(1L to 3L), 1, null)).count(normal) shouldBe 10
    }

    @Test
    fun `each tab keeps its own selection and badge while another tab is active`() {
        var selection = selectSubcategory(emptyMap(), 1, 2)
        val normalOnTest = base.activate(normal).withSelection(selection)
        normalOnTest.count(normal) shouldBe 2

        val onShounen = normalOnTest.activate(shounen)
        onShounen.count(normal) shouldBe 2
        onShounen.count(shounen) shouldBe 8

        selection = selectSubcategory(selection, 6, 7)
        val shounenOnWeekly = onShounen.withSelection(selection)
        shounenOnWeekly.count(shounen) shouldBe 7
        shounenOnWeekly.count(normal) shouldBe 2

        val backOnNormal = shounenOnWeekly.activate(normal)
        backOnNormal.activeCategory shouldBe normal
        backOnNormal.subcategoryNavigation(normal)!!.path shouldBe listOf(test)
        backOnNormal.getItemsForCategory(normal).map { it.id } shouldBe listOf(110L, 111L)
        backOnNormal.count(normal) shouldBe 2
        backOnNormal.count(shounen) shouldBe 7
    }

    @Test
    fun `nested selections include descendants, shared manga count once, empty children count zero`() {
        base.select(1, 4).count(normal) shouldBe 1
        // 111 is in Deep (below Test) and in Other: once in Test, once in Other, once in Normal.
        base.select(1, 2).getItemsForCategory(normal).map { it.id } shouldBe listOf(110L, 111L)
        base.count(normal) shouldBe 10
        base.select(1, 5).count(normal) shouldBe 0
    }

    @Test
    fun `membership changes update the badge of the selected subcategory`() {
        // 120 moves from Other to Test.
        val moved = state(memberships.map { if (it.first == 120L) 120L to listOf(2L) else it })
        moved.select(1, 2).count(normal) shouldBe 3
        moved.select(1, 3).count(normal) shouldBe 4
        // 110 removed from the library.
        state(memberships.filterNot { it.first == 110L }).select(1, 2).count(normal) shouldBe 1
    }

    @Test
    fun `search and filters narrow the selected count, the badge preference is respected`() {
        // The view model groups only the entries that pass search and filters.
        val searched = state(memberships.filter { it.first in setOf(111L, 120L, 200L) }, showMangaCount = false)
            .copy(searchQuery = "x")
        searched.select(1, 3).count(normal) shouldBe 2
        searched.select(1, 2).count(normal) shouldBe 1
        searched.count(normal) shouldBe 2

        base.copy(showMangaCount = false).select(1, 2).count(normal) shouldBe null
    }

    @Test
    fun `a deleted or foreign selection falls back to the whole tab`() {
        base.select(1, 99).count(normal) shouldBe 10
        base.select(1, 99).getItemsForCategory(normal).size shouldBe 10
        // Weekly belongs to Shounen, not Normal.
        base.select(1, 7).count(normal) shouldBe 10
        base.select(1, 7).getItemsForCategory(normal).size shouldBe 10
    }

    @Test
    fun `non-category grouping ignores subcategory selections`() {
        // A source tab whose id happens to match the Normal category.
        val sourceTab = cat(1, "Source", order = 0)
        val bySource = base.copy(
            groupType = LibraryGroup.BY_SOURCE,
            groupedFavorites = mapOf(sourceTab to memberships.map { it.first }),
        ).select(1, 2)
        bySource.count(sourceTab) shouldBe memberships.size
        bySource.getItemsForCategory(sourceTab).size shouldBe memberships.size
    }

    @Test
    fun `the toolbar counts the selected subcategory without tabs and the whole library with tabs`() {
        val selected = base.select(1, 2)
        selected.copy(showCategoryTabs = false).getToolbarTitle("Library", "Default", page = 0)
            .numberOfManga shouldBe 2
        selected.getToolbarTitle("Library", "Default", page = 0).numberOfManga shouldBe memberships.size
    }

    private fun LibraryViewModel.State.count(category: Category) = getItemCountForCategory(category)

    /** A state with only this selection. */
    private fun LibraryViewModel.State.select(rootId: Long, subcategoryId: Long?) =
        withSelection(selectSubcategory(emptyMap(), rootId, subcategoryId))

    private fun LibraryViewModel.State.withSelection(selection: Map<Long, Long>) =
        copy(subcategorySelection = selection)

    /** Mirrors [LibraryViewModel.selectSubcategory]. */
    private fun selectSubcategory(current: Map<Long, Long>, rootId: Long, subcategoryId: Long?) =
        if (subcategoryId == null || subcategoryId == rootId) current - rootId else current + (rootId to subcategoryId)

    private fun LibraryViewModel.State.activate(category: Category) = copy(
        activeCategoryId = category.id,
        activeCategoryIndex = displayedCategories.indexOf(category),
    )

    private fun state(
        memberships: List<Pair<Long, List<Long>>>,
        showMangaCount: Boolean = true,
    ): LibraryViewModel.State {
        val items = memberships.map { (id, categoryIds) -> item(id, categoryIds) }
        return LibraryViewModel.State(
            isInitialized = true,
            isLoading = false,
            showCategoryTabs = true,
            showMangaCount = showMangaCount,
            libraryData = LibraryViewModel.LibraryData(isInitialized = true, categories = categories, favorites = items),
            groupedFavorites = groupByCategoryTree(memberships, categories, showHiddenCategories = false),
        )
    }

    private fun item(id: Long, categoryIds: List<Long>) = LibraryItem(
        libraryManga = LibraryManga(
            manga = Manga.create().copy(id = id, favorite = true),
            categories = categoryIds,
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

    private fun cat(id: Long, name: String, parent: Long = 0, order: Long) =
        Category(id = id, name = name, order = order, flags = 0, hidden = false, parentId = parent)
}
// KNS <--
