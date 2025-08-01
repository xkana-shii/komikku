package eu.kanade.domain.track.interactor

import android.app.Application
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZoneOffset

class AddTracks(
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val trackerManager: TrackerManager,
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: Tracker, item: Track, mangaId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getChaptersByMangaId.await(mangaId)
            val hasReadChapters = allChapters.any { it.read }
            tracker.bind(item, hasReadChapters)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // TODO: merge into [SyncChapterProgressWithTrack]?
            // Update chapter progress if newer chapters marked read locally
            if (hasReadChapters) {
                val latestLocalReadChapterNumber = allChapters
                    .sortedBy { it.chapterNumber }
                    .takeWhile { it.read }
                    .lastOrNull()
                    ?.chapterNumber ?: -1.0

                if (latestLocalReadChapterNumber > track.lastChapterRead) {
                    /* KMK -->
                    // This code causes issue NOT settings remote-track's status
                    track = track.copy(
                        lastChapterRead = latestLocalReadChapterNumber,
                    )
                    KMK <-- */
                    tracker.setRemoteLastChapterRead(track.toDbTrack(), latestLocalReadChapterNumber.toInt())
                        // KMK -->
                        .toDomainTrack(idRequired = false)
                        ?.let { track = it }
                    // KMK <--
                }

                if (track.startDate <= 0) {
                    val firstReadChapterDate = Injekt.get<GetHistory>().await(mangaId)
                        .sortedBy { it.readAt }
                        .firstOrNull()
                        ?.readAt

                    firstReadChapterDate?.let {
                        val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        track = track.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                    }
                }
            }

            syncChapterProgressWithTrack.await(mangaId, track, tracker)
                // KMK -->
                ?.let {
                    val context = Injekt.get<Application>()
                    withUIContext {
                        context.toast(context.stringResource(KMR.strings.sync_progress_from_trackers_up_to_chapter, it))
                    }
                }
            // KMK <--
        }
    }

    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) = withNonCancellableContext {
        withIOContext {
            trackerManager.loggedInTrackers()
                .filterIsInstance<EnhancedTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(manga)?.let { track ->
                            track.manga_id = manga.id
                            (service as Tracker).bind(track)
                            insertTrack.await(track.toDomainTrack(idRequired = false)!!)

                            syncChapterProgressWithTrack.await(
                                manga.id,
                                track.toDomainTrack(idRequired = false)!!,
                                service,
                            )
                                // KMK -->
                                ?.let {
                                    val context = Injekt.get<Application>()
                                    withUIContext {
                                        context.toast(context.stringResource(KMR.strings.sync_progress_from_trackers_up_to_chapter, it))
                                    }
                                }
                            // KMK <--
                        }
                    } catch (e: Exception) {
                        logcat(
                            LogPriority.WARN,
                            e,
                        ) { "Could not match manga: ${manga.title} with service $service" }
                    }
                }
        }
    }
}
