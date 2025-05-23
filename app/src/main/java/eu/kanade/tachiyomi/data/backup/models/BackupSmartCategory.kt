package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.smartCategory.model.SmartCategory

@Serializable
class BackupSmartCategory(
    @ProtoNumber(1) var categoryId: Long = 0,
    @ProtoNumber(2) var categoryName: String,
    @ProtoNumber(3) var tags: List<String> = emptyList(),
)

val backupSmartCategoryMapper = { smartCategory: SmartCategory ->
    BackupSmartCategory(
        categoryId = smartCategory.categoryId,
        categoryName = smartCategory.categoryName,
        tags = smartCategory.tags,
    )
}
