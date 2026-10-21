package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryOf
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category

@Inject
class CategoriesBackupCreator(
    private val getCategories: GetCategories,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        // KNS -->
        val categories = getCategories.await().filterNot(Category::isSystemCategory)
        val byId = categories.associateBy(Category::id)
        return categories.map { backupCategoryOf(it, byId) }
        // KNS <--
    }
}
