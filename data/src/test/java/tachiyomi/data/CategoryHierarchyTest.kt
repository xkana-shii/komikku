package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.MergeCategories
import tachiyomi.domain.category.interactor.MoveCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.RestoredCategoryHierarchy
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

// KNS -->
/**
 * Hierarchical categories against the real schema: identity is (parent, name), never the name
 * alone (features.md § 1.1–1.3, acceptance scenarios 5–7).
 */
class CategoryHierarchyTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = DatabaseBindings.providesDatabase(driver)
    private val repository = CategoryRepositoryImpl(database)
    private val mangaRepository = MangaRepositoryImpl(database)
    private val preferences = InMemoryPreferenceStore()
    private val create = CreateCategoryWithName(repository, LibraryPreferences(preferences))
    private val deleted = mutableListOf<Long>()
    private val delete = DeleteCategory(repository, LibraryPreferences(preferences), DownloadPreferences(preferences)) {
        deleted += it
    }

    init {
        runBlocking { Database.Schema.create(driver).await() }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `same-named children under different parents are separate categories`() = runBlocking<Unit> {
        val (shounen, josei) = roots("Shounen", "Josei")
        create.await("Reading", shounen.id).shouldBeInstanceOf<CreateCategoryWithName.Result.Success>()
        create.await("Reading", josei.id).shouldBeInstanceOf<CreateCategoryWithName.Result.Success>()

        val readings = all().filter { it.name == "Reading" }
        readings.map(Category::parentId) shouldContainExactlyInAnyOrder listOf(shounen.id, josei.id)
        readings[0].uid shouldNotBe readings[1].uid
        readings.first { it.parentId == shounen.id }.uid shouldBe Category.childUid(shounen.uid, "Reading")

        // A second "reading" below the same parent is a duplicate; the existing one is returned.
        val duplicate = create.await(" reading ", shounen.id)
        duplicate.shouldBeInstanceOf<CreateCategoryWithName.Result.DuplicateName>()
        duplicate.existing.parentId shouldBe shounen.id
        // The global order stays depth-first.
        all().map { it.name } shouldBe listOf("Shounen", "Reading", "Josei", "Reading")
    }

    @Test
    fun `scenario 5 - renaming, moving and deleting one Reading leaves the other alone`() = runBlocking<Unit> {
        val (shounen, josei) = roots("Shounen", "Josei")
        create.await("Reading", shounen.id)
        create.await("Reading", josei.id)
        val sReading = all().first { it.name == "Reading" && it.parentId == shounen.id }
        val jReading = all().first { it.name == "Reading" && it.parentId == josei.id }
        addManga(1, sReading.id)
        addManga(2, jReading.id)

        val rename = RenameCategory(repository)
        rename.await(sReading.id, "Reading (weekly)") shouldBe RenameCategory.Result.Success
        repository.get(jReading.id)!!.name shouldBe "Reading"

        // Moving it under Josei would collide with Josei / Reading only if the names matched.
        MoveCategory(repository).await(sReading.id, josei.id, 0) shouldBe MoveCategory.Result.Success
        repository.get(jReading.id)!!.parentId shouldBe josei.id

        delete.await(sReading.id) shouldBe DeleteCategory.Result.Success
        repository.get(jReading.id) shouldNotBe null
        repository.getCategoriesByMangaId(2).map(Category::id) shouldBe listOf(jReading.id)
        repository.getCategoriesByMangaId(1) shouldBe emptyList()
    }

    @Test
    fun `renaming to a sibling's name is rejected, the same name under another parent is fine`() = runBlocking<Unit> {
        val (shounen, josei) = roots("Shounen", "Josei")
        create.await("Reading", shounen.id)
        create.await("Completed", shounen.id)
        create.await("Reading", josei.id)
        val sCompleted = all().first { it.name == "Completed" }
        val rename = RenameCategory(repository)
        rename.await(sCompleted.id, "reading") shouldBe RenameCategory.Result.DuplicateName
        val jReading = all().first { it.name == "Reading" && it.parentId == josei.id }
        rename.await(jReading.id, "Completed") shouldBe RenameCategory.Result.Success
    }

    @Test
    fun `a category cannot be moved below itself or its descendants`() = runBlocking<Unit> {
        val (shounen) = roots("Shounen")
        create.await("Reading", shounen.id)
        val reading = all().first { it.name == "Reading" }
        create.await("Weekly", reading.id)
        val weekly = all().first { it.name == "Weekly" }

        val move = MoveCategory(repository)
        move.await(shounen.id, weekly.id, 0) shouldBe MoveCategory.Result.InvalidMove
        move.await(shounen.id, shounen.id, 0) shouldBe MoveCategory.Result.InvalidMove
        // Moving a subtree to the root keeps it intact.
        move.await(reading.id, 0, 0) shouldBe MoveCategory.Result.Success
        all().map { it.name to it.parentId } shouldBe listOf(
            "Reading" to 0L,
            "Weekly" to reading.id,
            "Shounen" to 0L,
        )
    }

    @Test
    fun `scenario 6 - rapid reorders leave a consistent depth-first order`() = runBlocking<Unit> {
        val roots = roots("A", "B", "C", "D")
        create.await("A1", roots[0].id)
        create.await("A2", roots[0].id)
        val reorder = ReorderCategory(repository)
        repeat(20) { step ->
            val current = all().filter { it.parentId == 0L }
            reorder.await(current[step % current.size], (step * 3) % current.size)
        }
        val a1 = all().first { it.name == "A1" }
        reorder.await(a1, 1)

        val final = all()
        final.map(Category::order) shouldBe final.indices.map(Int::toLong)
        // Every child directly follows its parent's subtree.
        val aIndex = final.indexOfFirst { it.name == "A" }
        final.subList(aIndex + 1, aIndex + 3).map(Category::name) shouldBe listOf("A2", "A1")
        final.count { it.parentId == 0L } shouldBe 4
    }

    @Test
    fun `merging keeps memberships, moves children and leaves same-named categories elsewhere`() = runBlocking<Unit> {
        val (shounen, josei, seinen) = roots("Shounen", "Josei", "Seinen")
        create.await("Reading", shounen.id)
        create.await("Reading", josei.id)
        create.await("Weekly", seinen.id)
        val sReading = all().first { it.name == "Reading" && it.parentId == shounen.id }
        val jReading = all().first { it.name == "Reading" && it.parentId == josei.id }
        addManga(1, seinen.id)
        addManga(2, jReading.id)

        MergeCategories(repository, delete).await(targetId = shounen.id, sourceIds = listOf(seinen.id)) shouldBe
            MergeCategories.Result.Success

        repository.get(seinen.id) shouldBe null
        all().first { it.name == "Weekly" }.parentId shouldBe shounen.id
        repository.getCategoriesByMangaId(1).map(Category::id) shouldBe listOf(shounen.id)
        repository.get(sReading.id)!!.parentId shouldBe shounen.id
        repository.getCategoriesByMangaId(2).map(Category::id) shouldBe listOf(jReading.id)
        deleted shouldBe listOf(seinen.uid)
    }

    @Test
    fun `deleting a parent moves its children up instead of orphaning them`() = runBlocking<Unit> {
        val (shounen) = roots("Shounen")
        create.await("Reading", shounen.id)
        val reading = all().first { it.name == "Reading" }
        addManga(1, reading.id)

        delete.await(shounen.id) shouldBe DeleteCategory.Result.Success

        repository.get(reading.id)!!.parentId shouldBe 0L
        repository.getCategoriesByMangaId(1).map(Category::id) shouldBe listOf(reading.id)
    }

    @Test
    fun `scenario 7 - restore keeps same-named children apart and attaches them to their parents`() = runBlocking<Unit> {
        val shounenUid = Category.nameToUid("Shounen")
        val joseiUid = Category.nameToUid("Josei")
        val sReadingUid = Category.childUid(shounenUid, "Reading")
        val jReadingUid = Category.childUid(joseiUid, "Reading")
        val incoming = listOf(
            restoredCategory("Reading", order = 1, uid = sReadingUid, trackStatus = 1, interval = 7),
            restoredCategory("Shounen", order = 0, uid = shounenUid),
            restoredCategory("Josei", order = 2, uid = joseiUid),
            restoredCategory("Reading", order = 3, uid = jReadingUid, trackStatus = 1),
        )
        val hierarchy = RestoredCategoryHierarchy(
            parentUids = mapOf(shounenUid to 0L, joseiUid to 0L, sReadingUid to shounenUid, jReadingUid to joseiUid),
            carriesForkFields = setOf(shounenUid, joseiUid, sReadingUid, jReadingUid),
        )

        repository.restoreCategories(incoming, isSync = false, hierarchy = hierarchy)

        val restored = all()
        restored.map { it.name } shouldBe listOf("Shounen", "Reading", "Josei", "Reading")
        val byUid = restored.associateBy(Category::uid)
        byUid.getValue(sReadingUid).parentId shouldBe byUid.getValue(shounenUid).id
        byUid.getValue(jReadingUid).parentId shouldBe byUid.getValue(joseiUid).id
        byUid.getValue(sReadingUid).trackStatus shouldBe 1
        byUid.getValue(sReadingUid).updateInterval shouldBe 7

        // A second (ordinary) restore of the same backup changes nothing.
        repository.restoreCategories(incoming, isSync = false, hierarchy = hierarchy)
        all() shouldBe restored
    }

    @Test
    fun `a sync payload without hierarchy fields keeps the local parent and policies`() = runBlocking<Unit> {
        val (shounen) = roots("Shounen")
        create.await("Reading", shounen.id)
        val reading = all().first { it.name == "Reading" }
        repository.updateTrackStatus(reading.id, 1)
        repository.updateUpdateInterval(reading.id, 3)

        // A server that dropped unknown fields: same uid, no parent, no fork fields.
        repository.restoreCategories(
            listOf(
                restoredCategory("Shounen", order = 0, uid = shounen.uid),
                restoredCategory("Reading (renamed)", order = 1, uid = reading.uid),
            ),
            isSync = true,
            hierarchy = RestoredCategoryHierarchy(),
        )

        val after = repository.get(reading.id)!!
        after.name shouldBe "Reading (renamed)"
        after.parentId shouldBe shounen.id
        after.trackStatus shouldBe 1
        after.updateInterval shouldBe 3
    }

    @Test
    fun `a sync payload with hierarchy moves the category and converges its policies`() = runBlocking<Unit> {
        val (shounen, josei) = roots("Shounen", "Josei")
        create.await("Reading", shounen.id)
        val reading = all().first { it.name == "Reading" }

        repository.restoreCategories(
            listOf(restoredCategory("Reading", order = 2, uid = reading.uid, trackStatus = 5, interval = null)),
            isSync = true,
            hierarchy = RestoredCategoryHierarchy(
                parentUids = mapOf(reading.uid to josei.uid),
                carriesForkFields = setOf(reading.uid),
            ),
        )

        val after = repository.get(reading.id)!!
        after.parentId shouldBe josei.id
        after.trackStatus shouldBe 5
        all().map { it.name } shouldBe listOf("Shounen", "Josei", "Reading")
        // A sync restore is not a local edit: the version stays the server's.
        driver.long("SELECT is_syncing FROM category WHERE id = ${reading.id}") shouldBe 0L
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun roots(vararg names: String): List<Category> {
        names.forEach { create.await(it) }
        return all().filter { it.parentId == 0L && it.name in names }.sortedBy { names.indexOf(it.name) }
    }

    private suspend fun all(): List<Category> = repository.getAll().filterNot(Category::isSystemCategory)

    private suspend fun addManga(id: Long, categoryId: Long) {
        driver.execute(
            null,
            "INSERT OR IGNORE INTO manga(id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, " +
                "remote_memo, user_favorite_at, user_notes, user_reader_flags, user_chapter_flags, " +
                "state_chapter_fetch_interval, state_initialized) " +
                "VALUES ($id, 1, '/m$id', 'M$id', 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1)",
            0,
        ).await()
        mangaRepository.setMangaCategories(id, listOf(categoryId))
    }

    private fun restoredCategory(
        name: String,
        order: Long,
        uid: Long,
        trackStatus: Int? = null,
        interval: Int? = null,
    ) = Category(
        id = 0,
        name = name,
        order = order,
        flags = 0,
        hidden = false,
        uid = uid,
        trackStatus = trackStatus,
        updateInterval = interval,
    )

    private fun SqlDriver.long(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value
}
// KNS <--
