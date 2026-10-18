package tachiyomi.domain.category.model

data class CategoryUpdate(
    val id: Long,
    val name: String? = null,
    val order: Long? = null,
    val flags: Long? = null,
    // KMK -->
    val hidden: Boolean? = null,
    val parentId: Long? = null,
    val trackStatus: Int? = null,
    val updateInterval: Int? = null,
    // KMK <--
    // SY -->
    val version: Long? = null,
    val uid: Long? = null,
    val lastModifiedAt: Long? = null,
    // SY <--
)
