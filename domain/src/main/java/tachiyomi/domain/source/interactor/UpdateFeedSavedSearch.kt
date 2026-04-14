package tachiyomi.domain.source.interactor

import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

class UpdateFeedSavedSearch(
    private val FeedSavedSearchRepository: FeedSavedSearchRepository,
) {
    suspend fun await(update: FeedSavedSearchUpdate) {
        try {
            FeedSavedSearchRepository.updatePartial(update)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
        }
    }
}
