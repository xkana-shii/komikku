package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaUrlResolution
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.core.common.util.QuerySanitizer.sanitize

internal fun interface MangaBakaExternalSourceResolver {
    suspend fun resolve(query: String, trackerId: Long): MangaBakaUrlResolution
}

internal class TrackerSearchResolver(
    private val mangaBakaResolver: MangaBakaExternalSourceResolver,
) {
    suspend fun search(tracker: Tracker, query: String): List<TrackSearch> = when (
        val resolution = mangaBakaResolver.resolve(query, tracker.id)
    ) {
        is MangaBakaUrlResolution.ExternalSourceId -> listOfNotNull(tracker.searchById(resolution.value))
        MangaBakaUrlResolution.MissingExternalSource -> emptyList()
        MangaBakaUrlResolution.NotMangaBakaUrl -> tracker.search(query.sanitize())
    }
}
