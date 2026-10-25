package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.models.backupCategoryOf
import eu.kanade.tachiyomi.data.backup.restore.restorers.BackupCategoryHierarchy
import exh.source.MERGED_SOURCE_ID
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

// KNS -->
/**
 * Selective category backups: the selector's semantics, the manga and categories the real
 * [BackupCreator] and [MangaBackupCreator] write for a selection, and the worker input round trip.
 *
 * Normal(1) > Test(2) > Deep(4), Normal > Other(3); Shounen(5) > Reading(6); Josei(7) > Reading(8).
 */
class SelectiveBackupTest {

    private val normal = cat(1, "Normal", order = 0)
    private val test = cat(2, "Test", parent = 1, order = 1)
    private val other = cat(3, "Other", parent = 1, order = 2)
    private val deep = cat(4, "Deep", parent = 2, order = 3)
    private val shounen = cat(5, "Shounen", order = 4)
    private val sReading = cat(6, "Reading", parent = 5, order = 5)
    private val josei = cat(7, "Josei", order = 6)
    private val jReading = cat(8, "Reading", parent = 7, order = 7)
    private val system = Category(Category.UNCATEGORIZED_ID, "", -1, 0, false)
    private val categories = listOf(system, normal, test, other, deep, shounen, sReading, josei, jReading)

    // Selector ------------------------------------------------------------------------------------

    @Test
    fun `all is the default, clearing all selects nothing, and nothing is never everything`() {
        BackupCategorySelection.selectAll(true) shouldBe null
        BackupCategorySelection.selectAll(false) shouldBe emptySet()
        BackupCategorySelection.includesManga(null, listOf(2)) shouldBe true
        BackupCategorySelection.includesManga(emptySet(), listOf(2)) shouldBe false
        BackupCategorySelection.includesManga(emptySet(), emptyList()) shouldBe false
    }

    @Test
    fun `selecting a category selects its subtree, clearing it clears the subtree only`() {
        val none = BackupCategorySelection.selectAll(false)
        BackupCategorySelection.toggle(none, 1, true, categories) shouldBe setOf(1L, 2L, 3L, 4L)
        BackupCategorySelection.toggle(none, 2, true, categories) shouldBe setOf(2L, 4L)
        BackupCategorySelection.toggle(none, 4, true, categories) shouldBe setOf(4L)
        // Clearing a child keeps its parent and siblings.
        BackupCategorySelection.toggle(setOf(1L, 2L, 3L, 4L), 2, false, categories) shouldBe setOf(1L, 3L)
        // Same-named children under different parents are separate.
        BackupCategorySelection.toggle(none, 6, true, categories) shouldBe setOf(6L)
        // Clearing one category from "all" starts an explicit selection; selecting everything again is "all".
        val withoutJosei = BackupCategorySelection.toggle(null, 7, false, categories)
        withoutJosei shouldBe setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L)
        BackupCategorySelection.toggle(withoutJosei, 7, true, categories) shouldBe null
        BackupCategorySelection.toggle(none, 0, true, categories) shouldBe setOf(0L)
    }

    @Test
    fun `selector rows list Default first and nest subcategories under their parent`() {
        BackupCategorySelection.rows(categories).map { it.category.id to it.depth } shouldBe listOf(
            0L to 0, 1L to 0, 2L to 1, 4L to 2, 3L to 1, 5L to 0, 6L to 1, 7L to 0, 8L to 1,
        )
    }

    @Test
    fun `a manga is backed up when one of its categories is selected, uncategorized only with Default`() {
        val selection = setOf(2L, 4L)
        BackupCategorySelection.includesManga(selection, listOf(2)) shouldBe true
        BackupCategorySelection.includesManga(selection, listOf(1)) shouldBe false
        BackupCategorySelection.includesManga(selection, listOf(3, 4)) shouldBe true
        BackupCategorySelection.includesManga(selection, emptyList()) shouldBe false
        BackupCategorySelection.includesManga(setOf(0L), emptyList()) shouldBe true
    }

    // Creator -------------------------------------------------------------------------------------

    private val favorites = listOf(
        manga(10), // Normal only
        manga(11), // Test
        manga(12), // Deep and Other
        manga(13), // uncategorized
        manga(14), // Shounen > Reading
        manga(15), // Josei > Reading
        manga(16, source = MERGED_SOURCE_ID), // Test, merged entry of 17 and 18
        manga(18), // Josei, member of the merged entry
    )
    private val membership = mapOf(
        10L to listOf(normal),
        11L to listOf(test),
        12L to listOf(deep, other),
        13L to emptyList(),
        14L to listOf(sReading),
        15L to listOf(jReading),
        16L to listOf(test),
        18L to listOf(josei),
    )
    private val categoryRepository = mockk<CategoryRepository> {
        coEvery { getCategoriesByMangaIds(any()) } answers {
            firstArg<List<Long>>().associateWith { membership[it].orEmpty() }
        }
    }
    private val mangaRepository = mockk<MangaRepository>(relaxed = true) {
        coEvery { getReadMangaNotInLibrary() } returns listOf(manga(20, favorite = false))
    }
    private val categoriesBackupCreator = mockk<CategoriesBackupCreator>().also { creator ->
        val byId = categories.associateBy(Category::id)
        coEvery { creator.invoke() } returns categories.drop(1).map { backupCategoryOf(it, byId) }
    }
    private val creator = BackupCreator(
        isAutoBackup = false,
        context = mockk(relaxed = true),
        parser = ProtoBuf,
        getFavorites = mockk<GetFavorites> { coEvery { await() } returns favorites },
        backupPreferences = mockk(relaxed = true),
        mangaRepository = mangaRepository,
        categoriesBackupCreator = categoriesBackupCreator,
        mangaBackupCreator = mockk(relaxed = true),
        preferenceBackupCreator = mockk(relaxed = true),
        extensionStoresBackupCreator = mockk(relaxed = true),
        sourcesBackupCreator = mockk(relaxed = true),
        backupFileValidator = mockk(relaxed = true),
        feedBackupCreator = mockk(relaxed = true),
        savedSearchBackupCreator = mockk(relaxed = true),
        getMergedManga = mockk<GetMergedManga> { coEvery { await() } returns listOf(manga(17, favorite = false)) },
        webhookNotifier = mockk(relaxed = true),
        categoryRepository = categoryRepository,
        getMergedMangaById = mockk<GetMergedMangaById> {
            coEvery { await(16L) } returns listOf(manga(17, favorite = false), manga(18))
        },
    )

    private fun options(selection: Set<Long>?) = BackupOptions(includedCategoryIds = selection)

    @Test
    fun `a full backup selects the library, read entries and merged members as before`() = runBlocking<Unit> {
        creator.selectManga(options(null)).map { it.id } shouldBe favorites.map { it.id } + 20L + 17L
    }

    @Test
    fun `a child selection backs up that branch only, each manga once, with merged members`() = runBlocking<Unit> {
        val selected = creator.selectManga(options(setOf(2L, 4L)))
        // 11 (Test), 12 (Deep, also in Other) and the merged 16; its members 17 and 18 as non-library entries.
        selected.map { it.id } shouldBe listOf(11L, 12L, 16L, 17L, 18L)
        selected.filter { it.id in setOf(17L, 18L) }.map { it.favorite } shouldBe listOf(false, false)
    }

    @Test
    fun `parent, Default, same-named and empty selections pick exactly their entries`() = runBlocking<Unit> {
        creator.selectManga(options(setOf(1L, 2L, 3L, 4L))).map { it.id } shouldBe listOf(10L, 11L, 12L, 16L, 17L, 18L)
        creator.selectManga(options(setOf(0L))).map { it.id } shouldBe listOf(13L)
        creator.selectManga(options(setOf(6L))).map { it.id } shouldBe listOf(14L)
        creator.selectManga(options(setOf(8L))).map { it.id } shouldBe listOf(15L)
        creator.selectManga(options(emptySet())) shouldBe emptyList()
    }

    @Test
    fun `the backup keeps the selected categories and their ancestors, not unrelated ones`() = runBlocking<Unit> {
        creator.backupCategories(options(setOf(4L))).map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L, 4L)
        creator.backupCategories(options(setOf(8L))).map { it.id } shouldContainExactlyInAnyOrder listOf(7L, 8L)
        creator.backupCategories(options(setOf(0L))) shouldBe emptyList()
        creator.backupCategories(options(null)).size shouldBe 8
        creator.backupCategories(options(setOf(4L)).copy(categories = false)) shouldBe emptyList()
    }

    @Test
    fun `written manga only reference selected categories, which the backup's hierarchy resolves`() = runBlocking<Unit> {
        val mangaCreator = MangaBackupCreator(
            mangaRepository = mangaRepository,
            chapterRepository = mockk(relaxed = true),
            categoryRepository = categoryRepository,
            historyRepository = mockk(relaxed = true),
            trackRepository = mockk(relaxed = true),
            mangaMergeRepository = mockk(relaxed = true),
            sourceManager = mockk<SourceManager>().also { coEvery { it.get(any<Long>()) } returns null },
            getFlatMetadataById = mockk(relaxed = true),
        )
        val options = options(setOf(2L, 4L))
        val written = mangaCreator(creator.selectManga(options), options).toList().associateBy { it.url }
        // 12 is in Deep (selected) and Other (not): only Deep is written.
        written.getValue("/12").categories shouldBe listOf(deep.order)
        written.getValue("/11").categories shouldBe listOf(test.order)
        // 18 is a merged member filed in Josei, which is not part of this backup.
        written.getValue("/18").categories shouldBe emptyList()

        val hierarchy = BackupCategoryHierarchy(creator.backupCategories(options))
        written.values.flatMap { it.categories }.forEach { order ->
            hierarchy.nodeByOrder(order)!!.path shouldBe if (order == deep.order) {
                listOf("Normal", "Test", "Deep")
            } else {
                listOf("Normal", "Test")
            }
        }
    }

    // Worker --------------------------------------------------------------------------------------

    @Test
    fun `the worker input keeps the options array and tells all, none and some categories apart`() {
        val base = BackupOptions(chapters = false, privateSettings = true)
        listOf(null, emptySet(), setOf(0L, 4L, 8L)).forEach { selection ->
            val data = BackupCreateWorker.manualInputData("content://backup", base.copy(includedCategoryIds = selection))
            BackupCreateWorker.optionsOf(data) shouldBe base.copy(includedCategoryIds = selection)
        }
        // An automatic backup carries no options and backs up everything.
        BackupCreateWorker.optionsOf(androidx.work.Data.EMPTY) shouldBe BackupOptions()
    }

    private fun manga(id: Long, source: Long = 1, favorite: Boolean = true) =
        Manga.create().copy(id = id, source = source, favorite = favorite, url = "/$id", ogTitle = "Manga $id")

    private fun cat(id: Long, name: String, parent: Long = 0, order: Long) =
        Category(id = id, name = name, order = order, flags = 0, hidden = false, parentId = parent, uid = 0)
}
// KNS <--
