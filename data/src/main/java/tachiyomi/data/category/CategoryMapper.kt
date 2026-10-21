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
        // KMK <--
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
        // KNS -->
        parentId: Long,
        trackStatus: Long?,
        updateInterval: Long?,
        dateAdded: Long,
        dateModified: Long,
        // KNS <--
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            // KMK -->
            hidden = hidden == 1L,
            // KMK <--
            // SY -->
            version = version,
            uid = uid,
            lastModifiedAt = lastModifiedAt,
            // SY <--
            // KNS -->
            parentId = parentId,
            trackStatus = trackStatus?.toInt(),
            updateInterval = updateInterval?.toInt(),
            dateAdded = dateAdded,
            dateModified = dateModified,
            // KNS <--
        )
    }
}
