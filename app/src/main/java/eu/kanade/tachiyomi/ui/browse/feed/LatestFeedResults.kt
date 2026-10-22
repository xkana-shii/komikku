package eu.kanade.tachiyomi.ui.browse.feed

// KNS -->
/** Rejects a completed feed request after a newer request for the same feed has started. */
internal class LatestFeedResults {
    private var nextGeneration = 0L
    private val currentGenerations = mutableMapOf<Long, Long>()

    @Synchronized
    fun start(feedId: Long): Request {
        val request = Request(feedId, ++nextGeneration)
        currentGenerations[feedId] = request.generation
        return request
    }

    @Synchronized
    fun <T> accept(request: Request, result: T): T? =
        result.takeIf { currentGenerations[request.feedId] == request.generation }

    @Synchronized
    fun forget(feedId: Long) {
        currentGenerations.remove(feedId)
    }

    data class Request(val feedId: Long, val generation: Long)
}
// KNS <--
