package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        // KMK -->
        hidden: Long,
        parentId: Long,
        dateAdded: Long,
        dateModified: Long,
        trackStatus: Long?,
        updateInterval: Long?,
        // KMK <--
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
        )
    }
}
