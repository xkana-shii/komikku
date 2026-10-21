package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.backupCategoryOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.FetchInterval

// KNS -->
@OptIn(ExperimentalSerializationApi::class)
class BackupCategoryHierarchyTest {

    private val shounen = Category(id = 1, name = "Shounen", order = 0, flags = 0, hidden = false, uid = 11)
    private val sReading = Category(
        id = 2, name = "Reading", order = 1, flags = 0, hidden = false,
        uid = Category.childUid(11, "Reading"), parentId = 1, trackStatus = 1, updateInterval = 7,
    )
    private val josei = Category(id = 3, name = "Josei", order = 2, flags = 0, hidden = false, uid = 33)
    private val jReading = Category(
        id = 4,
        name = "Reading",
        order = 3,
        flags = 0,
        hidden = false,
        uid = Category.childUid(33, "Reading"),
        parentId = 3,
    )
    private val local = listOf(shounen, sReading, josei, jReading)

    @Test
    fun `backups carry the hierarchy and it survives protobuf encoding`() {
        val byId = local.associateBy(Category::id)
        val encoded = ProtoBuf.encodeToByteArray(local.map { backupCategoryOf(it, byId) })
        val decoded = ProtoBuf.decodeFromByteArray<List<BackupCategory>>(encoded)

        val reading = decoded.first { it.uid == sReading.uid }
        assertEquals(1L, reading.parentId)
        assertEquals(11L, reading.parentUid)
        assertEquals(1, reading.trackStatus)
        assertEquals(7, reading.updateInterval)
        assertEquals(true, reading.hierarchyKnown)

        val nodes = BackupCategoryHierarchy(decoded).nodes
        assertEquals(listOf(0, 0, 1, 1), nodes.map { it.depth })
        assertEquals(listOf("Shounen", "Reading"), nodes.first { it.uid == sReading.uid }.path)
        assertEquals(33L, nodes.first { it.uid == jReading.uid }.parentUid)
    }

    @Test
    fun `old fork backups resolve parents through the backup ids and derive distinct uids`() {
        // The previous personal fork wrote parentId (#901) but no uid, parentUid or hierarchyKnown.
        val oldFork = listOf(
            BackupCategory(name = "Shounen", order = 0, id = 5),
            BackupCategory(name = "Reading", order = 1, id = 6).apply { parentId = 5 },
            BackupCategory(name = "Josei", order = 2, id = 7),
            BackupCategory(name = "Reading", order = 3, id = 8).apply { parentId = 7 },
        )
        val nodes = BackupCategoryHierarchy(oldFork).nodes.associateBy { it.category.id }

        assertEquals(Category.nameToUid("Shounen"), nodes.getValue(5).uid)
        assertEquals(Category.childUid(Category.nameToUid("Shounen"), "Reading"), nodes.getValue(6).uid)
        assertNotEquals(nodes.getValue(6).uid, nodes.getValue(8).uid)
        assertEquals(nodes.getValue(5).uid, nodes.getValue(6).parentUid)
        // Roots of an old backup: parent unknown, not "root" (they may be a sync payload that lost it).
        assertNull(nodes.getValue(5).parentUid)
    }

    @Test
    fun `a manga's category reference resolves to the right same-named child`() = runTest {
        val restoreRepository: RestoreRepository = mockk(relaxed = true)
        val getCategories: GetCategories = mockk()
        coEvery { getCategories.await() } returns local
        val restorer = MangaRestorer(
            restoreRepository = restoreRepository,
            getCategories = getCategories,
            fetchInterval = mockk(relaxed = true),
        )
        restorer.isSync = false

        // A backup without uids (old fork): "Reading" under Josei has order 3.
        val backupCategories = listOf(
            BackupCategory(name = "Shounen", order = 0, id = 5),
            BackupCategory(name = "Reading", order = 1, id = 6).apply { parentId = 5 },
            BackupCategory(name = "Josei", order = 2, id = 7),
            BackupCategory(name = "Reading", order = 3, id = 8).apply { parentId = 7 },
        )
        val entries = slot<List<RestoredManga>>()
        coEvery { restoreRepository.restoreManga(capture(entries), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        restorer.restore(
            backupMangas = listOf(BackupManga(url = "/m", title = "M", categories = listOf(3L))),
            backupCategories = backupCategories,
        )

        assertEquals(listOf(jReading.id), entries.captured.single().categoryIds)
    }
}
// KNS <--
