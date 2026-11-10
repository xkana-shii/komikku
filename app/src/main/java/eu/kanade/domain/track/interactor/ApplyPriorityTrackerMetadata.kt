package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.sync.SyncStateBumper
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.data.track.model.hasAutofillData
import eu.kanade.tachiyomi.data.track.model.mergeAutofillTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetMangaUserInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.model.toMangaUserInfoUpdate
import tachiyomi.domain.track.interactor.GetTracks
import java.util.concurrent.ConcurrentHashMap

// KNS -->
/**
 * Applies the metadata of a manga's preferred tracker (its own preferred tracker, else the global priority tracker,
 * among the trackers actually linked to it) through the same persistent edited-details columns the manual
 * "Fill from tracker" editor writes, so the source can never overwrite it. Does nothing unless the
 * "Automatically use priority tracker metadata" setting is on.
 *
 * - Only fields the tracker provides are applied, one by one; blanks never replace existing values. Like the manual
 *   fill, a provided field replaces an existing edit of it, and a value equal to the source's clears the edit.
 * - A tracker that fails, is logged out or cannot provide metadata leaves everything as it was; no lower tracker
 *   takes over.
 * - Fetched metadata is reused for [CACHE_MS], and overlapping requests for one manga share one run.
 * - A result is only applied if the selected tracker is still the same once it arrives, and nothing is written when
 *   the details would not change. Tracking progress and chapters are never touched.
 */
@Inject
@SingleIn(AppScope::class)
class ApplyPriorityTrackerMetadata(
    private val trackPreferences: TrackPreferences,
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val getManga: GetManga,
    private val setMangaUserInfo: SetMangaUserInfo,
    private val syncStateBumper: SyncStateBumper,
) {

    private class Cached(val at: Long, val metadata: TrackMangaMetadata)

    private val cache = ConcurrentHashMap<Triple<Long, Long, Long>, Cached>()
    private val running = HashMap<Long, CompletableDeferred<Boolean>>()

    /** @return whether the manga's details changed. */
    suspend operator fun invoke(mangaId: Long, forceFetch: Boolean = false): Boolean {
        if (!trackPreferences.autoPriorityTrackerMetadata.get()) return false

        val mine = CompletableDeferred<Boolean>()
        val existing = synchronized(running) {
            running[mangaId] ?: run {
                running[mangaId] = mine
                null
            }
        }
        // An identical request is already running for this manga: share its outcome
        if (existing != null) return runCatching { existing.await() }.getOrDefault(false)

        return try {
            withContext(Dispatchers.IO) { run(mangaId, forceFetch) }.also { mine.complete(it) }
        } catch (e: Throwable) {
            mine.complete(false)
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Automatic tracker metadata failed for manga $mangaId" }
            false
        } finally {
            synchronized(running) { running.remove(mangaId) }
        }
    }

    private suspend fun run(mangaId: Long, forceFetch: Boolean): Boolean {
        val tracks = getTracks.await(mangaId)
        val chosenId = trackPreferences.resolvePreferredTracker(mangaId, tracks.mapTo(HashSet()) { it.trackerId })
            ?: return false
        val track = tracks.first { it.trackerId == chosenId }
        val tracker = trackerManager.get(chosenId) ?: return false
        if (!tracker.supportsMangaMetadata || !tracker.isLoggedIn) return false

        val key = Triple(mangaId, chosenId, track.remoteId)
        val now = System.currentTimeMillis()
        val metadata = cache[key]?.takeIf { !forceFetch && now - it.at < CACHE_MS }?.metadata
            ?: tracker.getMangaMetadata(track).also {
                if (cache.size > MAX_CACHED) cache.clear()
                cache[key] = Cached(now, it)
            }

        // The selection may have changed while fetching: a stale answer must not be applied
        val stillChosen = trackPreferences.resolvePreferredTracker(
            mangaId,
            getTracks.await(mangaId).mapTo(HashSet()) { it.trackerId },
        )
        if (stillChosen != chosenId || !trackPreferences.autoPriorityTrackerMetadata.get()) return false
        if (!metadata.hasAutofillData()) return false

        val manga = getManga.await(mangaId) ?: return false
        val current = manga.toMangaUserInfoUpdate()
        val updated = merge(manga, metadata)
        if (updated == current) return false

        setMangaUserInfo.setAll(listOf(updated))
        syncStateBumper.bumpForCustomInfoEdit(mangaId, current, updated)
        return true
    }

    companion object {
        const val CACHE_MS = 6L * 60 * 60 * 1000
        private const val MAX_CACHED = 300

        /** The manga's edited details with every field [metadata] provides applied. */
        fun merge(manga: Manga, metadata: TrackMangaMetadata): MangaUserInfoUpdate {
            val current = manga.toMangaUserInfoUpdate()

            fun text(fetched: String?, source: String?, edited: String?): String? {
                val value = fetched?.trim()?.takeIf { it.isNotEmpty() } ?: return edited
                return value.takeIf { it != source?.trim() }
            }

            val tags = autofillTags(metadata.tags.orEmpty())?.let { fetched ->
                val merged = mergeAutofillTags(manga.genre.orEmpty(), fetched)
                merged.takeIf { it != manga.ogGenre.orEmpty() }
            } ?: current.genre
            val status = metadata.status?.let { if (it != manga.ogStatus) it else null } ?: current.status

            return MangaUserInfoUpdate(
                id = manga.id,
                title = text(metadata.title, manga.ogTitle, current.title),
                author = text(metadata.authors, manga.ogAuthor, current.author),
                artist = text(metadata.artists, manga.ogArtist, current.artist),
                thumbnailUrl = text(metadata.thumbnailUrl, manga.ogThumbnailUrl, current.thumbnailUrl),
                description = text(metadata.description, manga.ogDescription, current.description),
                genre = tags,
                status = status,
            )
        }
    }
}
// KNS <--
