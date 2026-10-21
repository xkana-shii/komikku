package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.RestoredCategoryHierarchy
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CategoriesRestorer(
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {
    // SY -->
    var isSync: Boolean = false
    // SY <--

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getCategories.await()
        // KUN -->
        // KNS -->
        // Effective uids and parents come from the backup's hierarchy, so same-named categories
        // under different parents stay apart (a plain name-based uid would merge them).
        val hierarchy = BackupCategoryHierarchy(backupCategories)
        val categories = categoryRepository.restoreCategories(
            categories = hierarchy.nodes.map { node -> node.category.toCategory(id = 0).copy(uid = node.uid) },
            isSync = isSync,
            hierarchy = RestoredCategoryHierarchy(
                parentUids = hierarchy.nodes.mapNotNull { node -> node.parentUid?.let { node.uid to it } }.toMap(),
                carriesForkFields = hierarchy.nodes
                    .filter { it.category.hierarchyKnown || it.category.parentId != 0L }
                    .mapTo(mutableSetOf()) { it.uid },
            ),
        )
        // KNS <--
        // KUN <--

        libraryPreferences.categorizedDisplaySettings.set(
            (dbCategories + categories)
                .distinctBy { it.flags }
                .size > 1,
        )
    }
}
