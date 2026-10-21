package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

// KNS -->
/** The SyncYomi v2 category merge must keep same-named categories under different parents apart. */
class SyncCategoryHierarchyTest {

    private fun category(name: String, uid: Long, parentUid: Long = 0, order: Long) =
        BackupCategory(name = name, order = order, uid = uid).apply {
            this.parentUid = parentUid
            hierarchyKnown = true
        }

    @Test
    fun `same-named children survive the v2 merge`() {
        val local = listOf(
            category("Shounen", uid = 1, order = 0),
            category("Reading", uid = 11, parentUid = 1, order = 1),
            category("Josei", uid = 2, order = 2),
        )
        val remote = listOf(
            category("Josei", uid = 2, order = 2),
            category("Reading", uid = 22, parentUid = 2, order = 3),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        merged.map { it.uid } shouldContainExactlyInAnyOrder listOf(1L, 11L, 2L, 22L)
    }

    @Test
    fun `the uid-less name fallback is scoped to the parent`() {
        val local = listOf(
            category("Reading", uid = 0, parentUid = 1, order = 1),
            category("Reading", uid = 0, parentUid = 2, order = 3),
        )
        val remote = listOf(category("Reading", uid = 0, parentUid = 2, order = 5))

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        merged.map { it.parentUid to it.order } shouldContainExactlyInAnyOrder listOf(1L to 1L, 2L to 5L)
    }
}
// KNS <--
