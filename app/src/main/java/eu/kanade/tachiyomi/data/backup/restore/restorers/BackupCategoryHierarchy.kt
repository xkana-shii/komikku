package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Identity of the categories in one backup: each one's sync uid (the stored one, or the
 * deterministic one derived from its parent and name, exactly as a category created in the app
 * gets), its parent's uid and its name path. Shared by [CategoriesRestorer] and [MangaRestorer], so a
 * manga's category references and the restored categories always agree, and same-named categories
 * under different parents never collapse into one.
 *
 * The parent comes from [BackupCategory.parentUid], else from [BackupCategory.parentId] (the backup's
 * own ids; the only parent field of the previous personal fork's backups). Without either and
 * without [BackupCategory.hierarchyKnown] the parent is unknown, which is not the same as "root".
 */
class BackupCategoryHierarchy(categories: List<BackupCategory>) {

    class Node(
        val category: BackupCategory,
        val uid: Long,
        /** null = unknown, 0 = root. */
        val parentUid: Long?,
        val path: List<String>,
        val depth: Int,
    ) {
        val pathKey: String get() = path.joinToString("\u0000") { CategoryTree.normalizeName(it) }
    }

    val nodes: List<Node>

    private val byOrder: Map<Long, Node>

    init {
        val byBackupId = categories.filter { it.id != 0L }.associateBy { it.id }
        val uids = mutableMapOf<BackupCategory, Long>()
        val visiting = mutableSetOf<BackupCategory>()

        fun parentOf(category: BackupCategory): BackupCategory? {
            if (category.parentUid != 0L) {
                return categories.firstOrNull { it !== category && it.uid == category.parentUid }
            }
            return category.parentId.takeIf { it != 0L }?.let { byBackupId[it] }?.takeIf { it !== category }
        }

        fun uidOf(category: BackupCategory): Long {
            uids[category]?.let { return it }
            if (category.uid != 0L) return category.uid.also { uids[category] = it }
            if (!visiting.add(category)) return Category.nameToUid(category.name)
            val parentUid = category.parentUid.takeIf { it != 0L }
                ?: parentOf(category)?.let(::uidOf)
                ?: 0L
            visiting.remove(category)
            return Category.childUid(parentUid, category.name).also { uids[category] = it }
        }

        fun parentUidOf(category: BackupCategory): Long? = when {
            category.parentUid != 0L -> category.parentUid
            parentOf(category) != null -> uidOf(parentOf(category)!!)
            category.hierarchyKnown -> 0L
            else -> null
        }

        fun pathOf(category: BackupCategory): List<String> {
            val result = ArrayDeque(listOf(category.name))
            val seen = mutableSetOf(category)
            var parent = parentOf(category)
            while (parent != null && seen.add(parent)) {
                result.addFirst(parent.name)
                parent = parentOf(parent)
            }
            return result.toList()
        }

        nodes = categories.map { category ->
            val path = pathOf(category)
            Node(category, uidOf(category), parentUidOf(category), path, path.size - 1)
        }.sortedWith(compareBy<Node> { it.depth }.thenBy { it.category.order })
        byOrder = nodes.associateBy { it.category.order }
    }

    /** Mihon backups reference a manga's categories by the category's `order`. */
    fun nodeByOrder(order: Long): Node? = byOrder[order]
}
// KNS <--
