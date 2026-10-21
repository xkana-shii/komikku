package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.track.model.AutoRereadResetMode
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.time.Clock

// KNS -->
/**
 * Starts rereading a manga that is completed on its trackers: resets the read state of its chapters
 * ([AutoRereadResetMode]) and moves every linked tracker that knows a rereading status to it, with the
 * progress the reset leaves.
 */
@Inject
class StartReread(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val setReadStatus: SetReadStatus,
) {
    /** Logged-in trackers on which the manga is completed and that have a rereading status. */
    suspend fun candidates(mangaId: Long): List<Pair<Tracker, Track>> =
        getTracks.await(mangaId).mapNotNull { track ->
            val tracker = trackerManager.get(track.trackerId) ?: return@mapNotNull null
            (tracker to track).takeIf {
                tracker.isLoggedIn &&
                    tracker.getRereadingStatus() != NO_STATUS &&
                    track.status == tracker.getCompletionStatus()
            }
        }

    /**
     * Resets [chapters] (the visible chapters, in reading order) for [mode] and updates the trackers.
     * Returns the trackers that failed; the chapter reset is kept either way.
     */
    suspend fun await(
        mangaId: Long,
        chapters: List<Chapter>,
        currentChapter: Chapter?,
        mode: AutoRereadResetMode,
    ): List<Pair<Tracker, Throwable>> {
        val reset = chaptersToReset(chapters, currentChapter?.id, mode)
        if (reset.isNotEmpty()) setReadStatus.await(read = false, chapters = reset.toTypedArray())

        val progress = rereadProgress(currentChapter, mode)
        val results = trackerBatch(candidates(mangaId), upsertTrack::awaitOrThrow) {
            val current = refresh()
            update(
                current.copy(
                    status = service.getRereadingStatus(),
                    lastChapterRead = progress,
                    startDate = current.startDate ?: Clock.System.now(),
                ),
            )
        }
        return results.flatMap { result -> result.failures.map { result.service to it } }
    }

    companion object {
        private const val NO_STATUS = -1L

        /** The read chapters to mark unread: all of them, or those after the current one. */
        fun chaptersToReset(chapters: List<Chapter>, currentChapterId: Long?, mode: AutoRereadResetMode): List<Chapter> =
            when (mode) {
                AutoRereadResetMode.RESET_TO_ZERO -> chapters
                AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER -> {
                    val index = chapters.indexOfFirst { it.id == currentChapterId }
                    if (index < 0) emptyList() else chapters.drop(index + 1)
                }
            }.filter { it.read }

        /** Tracker progress after the reset: none, or the current chapter. */
        fun rereadProgress(currentChapter: Chapter?, mode: AutoRereadResetMode): Double = when (mode) {
            AutoRereadResetMode.RESET_TO_ZERO -> 0.0
            AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER ->
                currentChapter?.chapterNumber?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        }
    }
}
// KNS <--
