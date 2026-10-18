
package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaUrlResolution
import eu.kanade.tachiyomi.data.track.model.TrackSearch

/**
 * Routes normal text searches and MangaBaka URL lookups
 * through the correct tracker method.
 */
class TrackerSearchResolver(
    private val resolveMangaBakaUrl: suspend (
        query: String,
        trackerId: Long,
    ) -> MangaBakaUrlResolution,
) {

    suspend fun search(
        tracker: Tracker,
        query: String,
    ): List<TrackSearch> {
        return when (
            val resolution = resolveMangaBakaUrl(
                query,
                tracker.id,
            )
        ) {
            MangaBakaUrlResolution.NotMangaBakaUrl -> {
                tracker.search(query)
            }

            MangaBakaUrlResolution.MissingExternalSource -> {
                emptyList()
            }

            is MangaBakaUrlResolution.ExternalSourceId -> {
                tracker.searchById(resolution.id)
                    ?.let { listOf(it) }
                    ?: emptyList()
            }
        }
    }
}
