package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupSmartCategory
import eu.kanade.tachiyomi.data.backup.models.backupSmartCategoryMapper
import tachiyomi.domain.smartCategory.interactor.GetSmartCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartCategoriesBackupCreator(
    private val getSmartCategory: GetSmartCategory = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupSmartCategory> {
        return getSmartCategory.await().map(backupSmartCategoryMapper)
    }
}
