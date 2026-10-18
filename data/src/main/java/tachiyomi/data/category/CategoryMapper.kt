package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        // KMK -->
        @Suppress("UNUSED_PARAMETER") mangaOrder: List<Long>,
        hidden: Long,
        parentId: Long,
        dateAdded: Long,
        dateModified: Long,
        trackStatus: Long?,
        updateInterval: Long?,
        // KMK <--
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            // KMK -->
            hidden = hidden == 1L,
            parentId = parentId,
            dateAdded = dateAdded,
            dateModified = dateModified,
            trackStatus = trackStatus?.toInt(),
            updateInterval = updateInterval?.toInt(),
            // KMK <--
            // SY -->
            version = version,
            uid = uid,
            lastModifiedAt = lastModifiedAt,
            // SY <--
        )
    }
}
