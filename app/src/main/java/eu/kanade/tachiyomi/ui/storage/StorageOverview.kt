package eu.kanade.tachiyomi.ui.storage

import com.hippo.unifile.UniFile
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.manga.model.Manga
import java.util.Locale

// KNS -->
/** The storage one library entry uses: its downloads, or its folder for a local entry. */
data class StorageEntry(
    val manga: Manga,
    /** The entry's categories with their parents, so a parent category lists its subcategories' entries. */
    val categoryIds: Set<Long>,
    val size: Long,
    val chapterCount: Int,
)

/** Size and chapter count of one stored manga (a library entry or a merged entry's member). */
data class StoredSize(val size: Long, val chapterCount: Int)

object StorageOverview {
    /** The pseudo category that lists every entry. */
    const val ALL_CATEGORY_ID = -1L

    /**
     * The entries that use storage, largest first. A merged entry counts the downloads of its members
     * ([members]) and never anything stored under the merged entry itself.
     */
    fun entries(
        library: List<Pair<Manga, List<Long>>>,
        categories: List<Category>,
        members: (Manga) -> List<Manga>?,
        sizeOf: (Manga) -> StoredSize,
    ): List<StorageEntry> {
        val byId = categories.associateBy(Category::id)
        return library.distinctBy { it.first.id }
            .mapNotNull { (manga, categoryIds) ->
                val parts = members(manga)?.map(sizeOf) ?: listOf(sizeOf(manga))
                val size = parts.sumOf { it.size }
                if (size <= 0) return@mapNotNull null
                val assigned = categoryIds.filter { it != Category.UNCATEGORIZED_ID }
                StorageEntry(
                    manga = manga,
                    categoryIds = if (assigned.isEmpty()) {
                        setOf(Category.UNCATEGORIZED_ID)
                    } else {
                        CategoryTree.withAncestors(assigned, byId)
                    },
                    size = size,
                    chapterCount = parts.sumOf { it.chapterCount },
                )
            }
            .sortedByDescending { it.size }
    }

    fun filter(entries: List<StorageEntry>, categoryId: Long): List<StorageEntry> =
        if (categoryId == ALL_CATEGORY_ID) entries else entries.filter { categoryId in it.categoryIds }

    /** Total size of a file or directory tree; unreadable entries count as 0. */
    fun sizeOf(file: UniFile?): Long {
        if (file == null) return 0L
        if (!file.isDirectory) return file.length().coerceAtLeast(0L)
        return file.listFiles().orEmpty().sumOf { sizeOf(it) }
    }

    /** A short, readable size (decimal units, like the system storage settings). */
    fun formatSize(bytes: Long): String {
        val kb = 1000.0
        return when {
            bytes >= kb * kb * kb -> String.format(Locale.getDefault(), "%.2f GB", bytes / (kb * kb * kb))
            bytes >= kb * kb -> String.format(Locale.getDefault(), "%.2f MB", bytes / (kb * kb))
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
// KNS <--
