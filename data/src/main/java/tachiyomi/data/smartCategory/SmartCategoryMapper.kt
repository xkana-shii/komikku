package tachiyomi.data.smartCategory

import tachiyomi.domain.smartCategory.model.SmartCategory

object SmartCategoryMapper {
    fun mapSmartCategory(
        categoryId: Long,
        categoryName: String,
        tags: List<String>,
    ) = SmartCategory(
        categoryId = categoryId,
        categoryName = categoryName,
        tags = tags,
    )
}
