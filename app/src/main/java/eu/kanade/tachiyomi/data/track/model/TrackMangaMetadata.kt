package eu.kanade.tachiyomi.data.track.model

data class TrackMangaMetadata(
    val remoteId: Long? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val authors: String? = null,
    val artists: String? = null,
    // KNS -->
    /** Genres and tags as local manga tags; null when the provider has none. */
    val tags: List<String>? = null,
    /** The series' publication status as an `SManga` status; null keeps the current one. */
    val status: Long? = null,
    // KNS <--
)
