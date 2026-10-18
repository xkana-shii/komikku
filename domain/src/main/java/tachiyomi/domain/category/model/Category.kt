package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    // KMK -->
    val hidden: Boolean,
    val parentId: Long = UNCATEGORIZED_ID,
    val dateAdded: Long = DATE_UNKNOWN,
    val dateModified: Long = DATE_UNKNOWN,
    val trackStatus: Int? = null,
    val updateInterval: Int? = null,
    // KMK <--
    // SY -->
    val version: Long = 0,
    val uid: Long = 0,
    val lastModifiedAt: Long = 0,
    // SY <--
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
        // KMK -->
        const val DATE_UNKNOWN = 0L
        // KMK <--
    }
}
