package eu.kanade.tachiyomi.ui.browse.feed

import kotlinx.coroutines.CancellationException

// KMK --> Each load has a distinct identity, including structurally equal loading rows.
class FeedRequest

// An error retains the caller's last successful results, never an empty success.
internal suspend fun <T> feedRequest(
    onError: (Exception) -> T,
    fetch: suspend () -> T,
): T = try {
    fetch()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
}
// KMK <--
