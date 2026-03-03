package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.database.models.Track
import kotlinx.serialization.Serializable

@Serializable
data class MBRating(
    val rating: Double? = null,
)

fun MBRating.copyTo(track: Track): Track {
    return track.apply {
        this.score = rating ?: 0.0
    }
}
