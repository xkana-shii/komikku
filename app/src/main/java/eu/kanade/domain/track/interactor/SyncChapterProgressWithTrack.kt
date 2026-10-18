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
     * Synchronize chapter progress for an enhanced tracker.
     */
    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ): Int? {
        if (tracker !is EnhancedTracker) {
            return null
        }

        return sync(
            mangaId,
            remoteTrack,
            tracker,
        )
    }

    /**
     * Synchronize reading progress with any supported tracker.
     *
     * Local chapter updates are applied only when the tracker
     * has actually started reading the series.
     */
    suspend fun sync(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ): Int? {
        val dbChapters = getChaptersByMangaId.await(mangaId)
            .sortedByDescending { it.sourceOrder }
            .filter { it.isRecognizedNumber }

        val sortedChapters = dbChapters.sortedBy {
            it.chapterNumber
        }

        var checkingChapter = 0.0

        val chapterUpdates = dbChapters
            .takeWhile { chapter ->
                val lastCheckedChapter = checkingChapter
                checkingChapter = chapter.chapterNumber

                chapter.chapterNumber >= lastCheckedChapter &&
                    chapter.chapterNumber <= remoteTrack.lastChapterRead
            }
            .filterNot { it.read }
            .map { chapter ->
                ChapterUpdate(chapter.id) {
                    read = true
                }
            }

        val localLastRead = sortedChapters
            .takeWhile { it.read }
            .lastOrNull()
            ?.chapterNumber
            ?: 0.0

        val lastRead = max(
            remoteTrack.lastChapterRead,
            localLastRead,
        )

        val updatedTrack = remoteTrack.copy(
            lastChapterRead = lastRead,
        )

        try {
            if (lastRead > remoteTrack.lastChapterRead) {
                // KMK -->
                // The remote service may update status, start date,
                // finish date, and other personal tracking data.
                val remoteResult = tracker.update(
                    updatedTrack.toDbTrack(),
                    false,
                )

                val persisted = remoteResult
                    .toDomainTrack(idRequired = false)
                    ?.copy(
                        id = remoteTrack.id,
                        mangaId = remoteTrack.mangaId,
                        trackerId = remoteTrack.trackerId,
                    )
                    ?: updatedTrack

                upsertTrack.await(persisted)
                // KMK <--
            }

            if (
                chapterUpdates.isNotEmpty() &&
                !tracker.hasNotStartedReading(remoteTrack.status)
            ) {
                updateChapter.awaitAll(chapterUpdates)
                return lastRead.toInt()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) {
                "Failed to synchronize chapter progress for manga $mangaId"
            }
        }

        return null
    }
}
