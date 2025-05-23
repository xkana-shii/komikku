package tachiyomi.domain.smartCategory.model

import java.io.Serializable

data class SmartCategory(
    val categoryId: Long,
    val categoryName: String,
    val tags: List<String>,
) : Serializable
