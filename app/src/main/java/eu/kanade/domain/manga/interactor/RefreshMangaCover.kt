package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.log.xLogI
import exh.log.xLogW
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

// KUN -->
enum class CoverPersistResult {
    Persisted,
    SkippedWhileSyncing,
    Obsolete,
    Failed,
}
// KUN <--

/**
 * Recovers a stale source cover when loading the stored URL fails.
 *
 * The source is asked for the manga's details again, but only the cover URL is persisted:
 * title, description, status, chapters, etc. stay untouched. This mirrors what a manual
 * refresh would fix, without turning a failed image load into a full entry refresh.
 *
 * Source calls are deliberately conservative:
 * - only one refresh per manga at a time, and at most one per [MANGA_COOLDOWN_MS] for the same
 *   stale URL;
 * - cooldowns are 6 hours for definitive answers, 10 minutes for transient source failures and
 *   15 minutes for dead replacements;
 * - starts are paced per source ([SOURCE_MIN_INTERVAL_MS]), with a safety-valve budget
 *   ([MAX_ATTEMPTS_PER_SOURCE] per [SOURCE_WINDOW_MS]) and a per-source circuit breaker that
 *   pauses a source after [SOURCE_FAILURE_THRESHOLD] consecutive failures;
 * - at most [MAX_CONCURRENT_REQUESTS] source requests in flight across the app.
 */
@Inject
@SingleIn(AppScope::class)
class RefreshMangaCover(
    private val mangaRepository: MangaRepository,
    private val sourceManager: SourceManager,
    private val coverCache: CoverCache,
    // Injectable for deterministic tests; production uses a monotonic clock.
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private data class InFlightRefresh(
        val staleUrl: String,
        val result: CompletableDeferred<String?>,
    )

    private data class Replacement(
        val staleUrl: String,
        val newUrl: String,
    )

    private data class Attempt(
        val staleUrl: String,
        val at: Long,
        val cooldownMs: Long,
    )

    private val inFlight = ConcurrentHashMap<Long, InFlightRefresh>()
    private val lastReplacement = ConcurrentHashMap<Long, Replacement>()
    private val lastAttemptAt = ConcurrentHashMap<Long, Attempt>()
    private val sourceAttempts = HashMap<Long, ArrayDeque<Long>>()
    private val sourceNextAttemptAt = HashMap<Long, Long>()
    private val sourceConsecutiveFailures = HashMap<Long, Int>()
    private val sourceBlockedUntil = HashMap<Long, Long>()
    private val sourceAttemptsLock = Any()
    private val sourceSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    /**
     * Asks the source for a replacement cover URL. Nothing is persisted here: the image is
     * persisted with [persistReplacement] only after it has been fetched successfully.
     *
     * @param mangaId the manga whose cover failed to load.
     * @param staleUrl the URL that failed, used to ensure the entry has not been refreshed meanwhile.
     * @return the new cover URL if the source provided a different one, otherwise `null`.
     */
    suspend fun findReplacement(mangaId: Long, staleUrl: String?): String? {
        if (mangaId <= 0 || staleUrl.isNullOrBlank()) return null

        // Join an existing refresh instead of starting another one for the same entry.
        inFlight[mangaId]?.let { current ->
            return if (current.staleUrl == staleUrl) current.result.await() else null
        }

        val refresh = InFlightRefresh(staleUrl, CompletableDeferred())
        val leader = inFlight.putIfAbsent(mangaId, refresh)
        if (leader != null) {
            return if (leader.staleUrl == staleUrl) leader.result.await() else null
        }

        try {
            // Re-check after becoming the leader: another request may have finished and recorded a
            // cooldown between the lookup above and the putIfAbsent.
            if (isCoolingDown(mangaId, staleUrl)) {
                refresh.result.complete(lastReplacement[mangaId]?.takeIf { it.staleUrl == staleUrl }?.newUrl)
            } else {
                refresh.result.complete(fetchReplacement(mangaId, staleUrl))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogW("Could not refresh the cover of manga $mangaId", e)
            refresh.result.complete(null)
        } finally {
            if (!refresh.result.isCompleted) refresh.result.complete(null)
            inFlight.remove(mangaId, refresh)
        }
        return refresh.result.await()
    }

    /**
     * Persists [newUrl] for [mangaId], but only if the entry still points at [staleUrl].
     * Called after the replacement image has been fetched so a dead replacement is not stored.
     *
     * [CoverPersistResult.SkippedWhileSyncing] and [CoverPersistResult.Failed] leave the
     * replacement eligible for a later retry; [CoverPersistResult.Obsolete] means the entry
     * moved on and the replacement should be treated as dead.
     */
    suspend fun persistReplacement(mangaId: Long, staleUrl: String?, newUrl: String): CoverPersistResult {
        if (mangaId <= 0 || staleUrl.isNullOrBlank() || newUrl == staleUrl) {
            return CoverPersistResult.Obsolete
        }
        return try {
            val manga = mangaRepository.getMangaById(mangaId)

            // Non-library rows have no source cover to repair, and a user-provided cover must
            // never be replaced.
            if (!manga.favorite) return CoverPersistResult.Obsolete
            if (manga.thumbnailUrl != manga.ogThumbnailUrl) return CoverPersistResult.Obsolete
            if (coverCache.hasCustomCover(mangaId)) return CoverPersistResult.Obsolete
            // A concurrent recovery may have already written the same URL. Treat it as success so
            // the caller can keep the winner's in-memory alias instead of invalidating it.
            if (manga.ogThumbnailUrl == newUrl) return CoverPersistResult.Persisted
            // A manual refresh may have updated the entry while the image was being fetched.
            if (manga.ogThumbnailUrl != staleUrl) return CoverPersistResult.Obsolete
            if (mangaRepository.isSyncing(mangaId)) return CoverPersistResult.SkippedWhileSyncing

            // The WHERE clause keeps a concurrent manual refresh from being overwritten even
            // if it happens between the checks above and the write. It also fails while a sync
            // is applying (`is_syncing != 0`) so the recovery can be retried afterwards. Only the
            // URL changes: keeping the Coil cache timestamp stable lets the already decoded image
            // stay in memory. The write bumps `version` explicitly because routine cover churn no
            // longer does; the triggers still stamp `last_modified_at` for the next sync.
            if (!mangaRepository.updateCover(mangaId, staleUrl, newUrl)) {
                return classifyFailedWrite(mangaId, staleUrl, newUrl)
            }
            coverCache.deleteFromCache(manga, false)
            xLogI("Refreshed stale cover for manga $mangaId")
            CoverPersistResult.Persisted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogW("Could not persist the refreshed cover of manga $mangaId", e)
            CoverPersistResult.Failed
        }
    }

    /**
     * Explains an `updateCover` miss without treating a concurrent winner or an active sync as
     * a dead replacement.
     */
    private suspend fun classifyFailedWrite(mangaId: Long, staleUrl: String, newUrl: String): CoverPersistResult {
        val current = mangaRepository.getMangaById(mangaId)
        return when {
            current.ogThumbnailUrl == newUrl -> CoverPersistResult.Persisted
            mangaRepository.isSyncing(mangaId) -> CoverPersistResult.SkippedWhileSyncing
            current.ogThumbnailUrl != staleUrl -> CoverPersistResult.Obsolete
            else -> CoverPersistResult.Failed
        }
    }

    /**
     * Marks [newUrl] as dead so it is not used again immediately. Called when persisting it
     * failed or when loading it failed right after the source offered it.
     */
    fun invalidateReplacement(mangaId: Long, newUrl: String) {
        if (mangaId <= 0) return
        val replacement = lastReplacement[mangaId] ?: return
        if (replacement.newUrl != newUrl) return
        if (!lastReplacement.remove(mangaId, replacement)) return
        recordAttempt(mangaId, replacement.staleUrl, DEAD_REPLACEMENT_COOLDOWN_MS)
        xLogW("Cover replacement for manga $mangaId did not load; backing off")
    }

    private suspend fun fetchReplacement(mangaId: Long, staleUrl: String): String? {
        val manga = mangaRepository.getMangaById(mangaId)
        val currentUrl = manga.thumbnailUrl

        // Non-library rows have no source cover to repair and would only cause source calls.
        if (!manga.favorite) return null
        // Never override a user-provided cover, and only act on the entry we failed to load.
        if (currentUrl == null || currentUrl != staleUrl) return null
        if (currentUrl != manga.ogThumbnailUrl) return null
        if (coverCache.hasCustomCover(mangaId)) return null

        val source = sourceManager.get(manga.source) as? HttpSource ?: return null
        val remote = try {
            // Time-box waiting for a permit and the pacing delay as well, not just the request.
            // Run everything on IO so the timeout uses a real clock instead of a test scheduler.
            withContext(Dispatchers.IO) {
                withTimeout(SOURCE_TIMEOUT_MS) {
                    sourceSemaphore.withPermit {
                        // Pace at the actual request start, not when the caller started waiting for a slot.
                        if (!awaitSourceSlot(source.id)) {
                            xLogW("Cover refresh for manga $mangaId paused (budget/breaker) for source ${source.id}")
                            return@withPermit null
                        }
                        source.getMangaUpdate(
                            manga = manga.toSManga(),
                            chapters = emptyList(),
                            fetchDetails = true,
                            fetchChapters = false,
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // If the outer request was cancelled at the same time, propagate that instead of
            // treating the timeout as a transient source failure.
            coroutineContext.ensureActive()
            recordSourceFailure(source.id)
            recordAttempt(mangaId, staleUrl, TRANSIENT_COOLDOWN_MS)
            null
        } catch (e: CancellationException) {
            // A cancelled image request should not lock this entry out for the whole cooldown.
            throw e
        } catch (e: Exception) {
            recordSourceFailure(source.id)
            recordAttempt(mangaId, staleUrl, TRANSIENT_COOLDOWN_MS)
            throw e
        } ?: return null
        recordSourceSuccess(source.id)

        val newUrl = remote.manga.thumbnail_url
            ?.trim()
            ?.toHttpUrlOrNull()
            ?.toString()

        // The source still advertises the URL that just failed (or gave nothing usable); nothing
        // useful to try, but do not ask again for a while.
        if (newUrl == null || newUrl == currentUrl) {
            recordAttempt(mangaId, staleUrl, MANGA_COOLDOWN_MS)
            return null
        }

        recordAttempt(mangaId, staleUrl, MANGA_COOLDOWN_MS)
        lastReplacement[mangaId] = Replacement(staleUrl, newUrl)
        return newUrl
    }

    private fun isCoolingDown(mangaId: Long, staleUrl: String): Boolean {
        val attempt = lastAttemptAt[mangaId] ?: return false
        if (attempt.staleUrl != staleUrl) return false
        return now() - attempt.at < attempt.cooldownMs
    }

    private fun recordAttempt(mangaId: Long, staleUrl: String, cooldownMs: Long) {
        lastAttemptAt[mangaId] = Attempt(staleUrl, now(), cooldownMs)
    }

    /**
     * Waits until this source may be queried again. Requests are intentionally serialized per
     * source: a host migration can leave many covers stale at once, and a one-time repair should
     * still reach the source at a modest, steady rate instead of a burst.
     */
    private suspend fun awaitSourceSlot(sourceId: Long): Boolean {
        while (true) {
            val waitMs = synchronized(sourceAttemptsLock) {
                val currentTime = now()
                val blockedUntil = sourceBlockedUntil[sourceId] ?: 0L
                if (currentTime < blockedUntil) return false
                val next = sourceNextAttemptAt[sourceId] ?: 0L
                if (currentTime >= next) {
                    if (!hasSourceBudget(sourceId, currentTime)) {
                        sourceBlockedUntil[sourceId] = currentTime + SOURCE_BUSY_COOLDOWN_MS
                        return false
                    }
                    // Do not consume the budget when the caller was cancelled while waiting.
                    coroutineContext.ensureActive()
                    sourceNextAttemptAt[sourceId] = currentTime + SOURCE_MIN_INTERVAL_MS
                    recordSourceAttempt(sourceId, currentTime)
                    return true
                }
                next - currentTime
            }
            delay(waitMs)
        }
    }

    private fun recordSourceSuccess(sourceId: Long) {
        synchronized(sourceAttemptsLock) {
            sourceConsecutiveFailures.remove(sourceId)
            sourceBlockedUntil.remove(sourceId)
        }
    }

    private fun recordSourceFailure(sourceId: Long) {
        val blocked = synchronized(sourceAttemptsLock) {
            val failures = (sourceConsecutiveFailures[sourceId] ?: 0) + 1
            sourceConsecutiveFailures[sourceId] = failures
            if (failures >= SOURCE_FAILURE_THRESHOLD) {
                sourceBlockedUntil[sourceId] = now() + SOURCE_FAILURE_COOLDOWN_MS
                true
            } else {
                false
            }
        }
        if (blocked) {
            xLogW(
                "Cover recovery for source $sourceId paused after " +
                    "$SOURCE_FAILURE_THRESHOLD consecutive failures",
            )
        }
    }

    private fun hasSourceBudget(sourceId: Long, now: Long): Boolean {
        val attempts = sourceAttempts.getOrPut(sourceId) { ArrayDeque() }
        while (attempts.isNotEmpty() && now - attempts.first() >= SOURCE_WINDOW_MS) {
            attempts.removeFirst()
        }
        return attempts.size < MAX_ATTEMPTS_PER_SOURCE
    }

    private fun recordSourceAttempt(sourceId: Long, now: Long) {
        sourceAttempts.getOrPut(sourceId) { ArrayDeque() }.addLast(now)
    }

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 2
        const val MANGA_COOLDOWN_MS = 6 * 60 * 60 * 1000L // 6 hours
        const val TRANSIENT_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes
        const val DEAD_REPLACEMENT_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes
        const val SOURCE_TIMEOUT_MS = 60_000L
        const val SOURCE_FAILURE_THRESHOLD = 5
        const val SOURCE_FAILURE_COOLDOWN_MS = 2 * 60 * 60 * 1000L // 2 hours
        const val SOURCE_BUSY_COOLDOWN_MS = 60_000L
        // A one-time host migration can leave a whole library stale. These are a safety valve,
        // not a typical limit: the per-manga cooldown is what prevents repeated requests.
        const val MAX_ATTEMPTS_PER_SOURCE = 200
        const val SOURCE_WINDOW_MS = 10 * 60 * 1000L // 10 minutes
        const val SOURCE_MIN_INTERVAL_MS = 300L
    }
}
