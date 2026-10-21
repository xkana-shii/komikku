package eu.kanade.tachiyomi.ui.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga

// KNS -->
class StorageOverviewTest {
    private val categories = listOf(
        Category(id = 1, name = "Shounen", order = 1, flags = 0, hidden = false),
        Category(id = 2, name = "Reading", order = 2, flags = 0, hidden = false, parentId = 1),
    )
    private val small = manga(1)
    private val big = manga(2)
    private val empty = manga(3)
    private val merged = manga(4)
    private val memberA = manga(5)
    private val memberB = manga(6)
    private val sizes = mapOf(1L to 10L, 2L to 500L, 3L to 0L, 4L to 999L, 5L to 40L, 6L to 60L)

    private fun entries() = StorageOverview.entries(
        library = listOf(small to listOf(2L), big to listOf(0L), empty to listOf(1L), merged to listOf(1L), small to listOf(2L)),
        categories = categories,
        members = { if (it.id == 4L) listOf(memberA, memberB) else null },
        sizeOf = { StoredSize(sizes.getValue(it.id), chapterCount = if (sizes.getValue(it.id) > 0) 1 else 0) },
    )

    @Test
    fun `entries are listed once, largest first, without empty ones`() {
        entries().map { it.manga.id } shouldBe listOf(2L, 4L, 1L)
    }

    @Test
    fun `a merged entry counts its members, not the merged entry itself`() {
        val mergedEntry = entries().single { it.manga.id == 4L }
        mergedEntry.size shouldBe 100L
        mergedEntry.chapterCount shouldBe 2
    }

    @Test
    fun `a parent category lists its subcategories' entries`() {
        StorageOverview.filter(entries(), 1).map { it.manga.id } shouldBe listOf(4L, 1L)
        StorageOverview.filter(entries(), 2).map { it.manga.id } shouldBe listOf(1L)
        StorageOverview.filter(entries(), 0).map { it.manga.id } shouldBe listOf(2L)
        StorageOverview.filter(entries(), StorageOverview.ALL_CATEGORY_ID).size shouldBe 3
    }

    @Test
    fun `sizes are formatted in decimal units`() {
        StorageOverview.formatSize(999) shouldBe "999 B"
        StorageOverview.formatSize(1_500).replace(',', '.') shouldBe "1.50 KB"
        StorageOverview.formatSize(2_000_000_000).replace(',', '.') shouldBe "2.00 GB"
    }

    private fun manga(id: Long) = Manga.create().copy(id = id, ogTitle = "Manga $id", favorite = true)
}
// KNS <--
