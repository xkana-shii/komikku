package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.math.max

@Inject
class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val upsertTrack: UpsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {
    /**
     * Sync chapter progress with the [EnhancedTracker]
     */
    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ): Int? {
        if (tracker !is EnhancedTracker) {
            return null
        }
        // KMK -->
        return sync(mangaId, remoteTrack, tracker)
    }

    /**
     * Sync chapter progress with the all trackers.
     */
    suspend fun sync(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ): Int? {
        // KMK <--
        // Current chapters in database, sort by source's order because database's order is a mess
        val dbChapters = getChaptersByMangaId.await(mangaId)
            // KMK -->
            .sortedByDescending { it.sourceOrder }
            // KMK <--
            .filter { it.isRecognizedNumber }

        val sortedChapters = dbChapters
            .sortedBy { it.chapterNumber }

        // KMK -->
        var lastCheckChapter: Double
        var checkingChapter = 0.0

        /**
         * Chapters to update to follow tracker: only continuous incremental chapters
         * any abnormal chapter number will stop it from updating read status further.
         * Some mangas has name such as Volume 2 Chapter 1 which will corrupt the order
         * if we sort by chapterNumber.
         */
        val chapterUpdates = dbChapters
            .takeWhile { chapter ->
                lastCheckChapter = checkingChapter
                checkingChapter = chapter.chapterNumber
                chapter.chapterNumber >= lastCheckChapter && chapter.chapterNumber <= remoteTrack.lastChapterRead
            }
            .filter { chapter -> !chapter.read }
            // KMK <--
            .map { ChapterUpdate(it.id) { read = true } }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        // Tracker will update to latest read chapter
        val lastRead = max(remoteTrack.lastChapterRead, localLastRead.toDouble())
        val updatedTrack = remoteTrack.copy(lastChapterRead = lastRead)

        try {
            // Update Tracker to localLastRead if needed
            if (lastRead > remoteTrack.lastChapterRead) {
                // KNS --> save the record the service returned (it may adjust status or dates)
                val saved = tracker.update(updatedTrack.toDbTrack()).toDomainTrack() ?: updatedTrack
                // update Track in database
                upsertTrack.await(saved)
                // KNS <--
            }
            // KMK -->
            // Always update local chapters following Tracker even past chapters
            if (chapterUpdates.isNotEmpty() &&
                !tracker.hasNotStartedReading(remoteTrack.status)
            ) {
                updateChapter.awaitAll(chapterUpdates)
                return lastRead.toInt()
            }
            // KMK <--
            // KNS -->
        } catch (e: CancellationException) {
            throw e
            // KNS <--
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
        // KMK -->
        return null
        // KMK <--
    }
}
