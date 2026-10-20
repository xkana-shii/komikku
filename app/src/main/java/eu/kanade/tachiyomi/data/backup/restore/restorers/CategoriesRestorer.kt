package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
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
        val categories = categoryRepository.restoreCategories(
            categories = backupCategories
                .sortedBy { it.order }
                .map {
                    val uid = if (it.uid != 0L) it.uid else Category.nameToUid(it.name)
                    it.toCategory(id = 0).copy(uid = uid)
                },
            isSync = isSync,
        )
        // KUN <--

        libraryPreferences.categorizedDisplaySettings.set(
            (dbCategories + categories)
                .distinctBy { it.flags }
                .size > 1,
        )
    }
}
