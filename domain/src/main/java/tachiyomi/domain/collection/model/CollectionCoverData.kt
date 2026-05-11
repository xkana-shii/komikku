package tachiyomi.domain.collection.model

data class CollectionCoverData(
    val mangaId: Long,
    val sourceId: Long,
    val ogThumbnailUrl: String?,
    val coverLastModified: Long,
    val isFavorite: Boolean,
)
