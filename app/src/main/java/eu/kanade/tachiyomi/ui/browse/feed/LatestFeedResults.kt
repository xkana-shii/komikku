package eu.kanade.tachiyomi.ui.browse.feed

import tachiyomi.domain.source.model.SavedSearch

// KNS -->
/** The immutable source/search inputs that produced a feed request. */
internal data class FeedRequestConfiguration(
    val sourceId: Long,
    val savedSearch: SavedSearch?,
    val kind: Kind,
) {
    enum class Kind { SAVED_SEARCH, LATEST, POPULAR }
}

/** Rejects a completion after either a newer generation or a different configuration is active. */
internal class LatestFeedResults {
    private var nextGeneration = 0L
    private val currentGenerations = mutableMapOf<Long, Long>()

    @Synchronized
    fun start(feedId: Long, configuration: FeedRequestConfiguration): Request {
        val request = Request(feedId, ++nextGeneration, configuration)
        currentGenerations[feedId] = request.generation
        return request
    }

    @Synchronized
    fun <T> accept(request: Request, activeConfiguration: FeedRequestConfiguration?, result: T): T? =
        result.takeIf {
            currentGenerations[request.feedId] == request.generation &&
                activeConfiguration == request.configuration
        }

    @Synchronized
    fun forget(feedId: Long) {
        currentGenerations.remove(feedId)
    }

    data class Request(
        val feedId: Long,
        val generation: Long,
        val configuration: FeedRequestConfiguration,
    )
}
// KNS <--
