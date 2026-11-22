package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import android.os.Build
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.data.sync.chapterTombstoneKey
import eu.kanade.tachiyomi.data.sync.dropTombstonedHistory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.repository.PendingHistoryDeletion
import tachiyomi.i18n.MR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    // KUN -->
    // Metro DI: the shared ProtoBuf singleton replaces the old `Injekt.get()` default.
    private val protoBuf: ProtoBuf = ProtoBuf,
    // The history tombstone queue lives on the rows (`history.pending_deletion`), so the deletions
    // request reads and clears it here rather than through a preference set.
    private val historyRepository: HistoryRepository,
    // KUN <--
) : SyncService(context, json, syncPreferences) {

    // KMK -->
    companion object {
        private val client by lazy { OkHttpClient() }

        // SY -->
        val TIMEOUT = 30.seconds
        val FULL_SYNC_INTERVAL = 24.hours
        // SY <--
    }
    // KMK <--

    private class SyncYomiException(
        message: String?,
        // KUN -->
        // The HTTP status of a failed request, when there was one. SyncManager uses it to tell a
        // permanent rejection (a force push that can never fit) from a transient failure.
        val statusCode: Int? = null,
        // KUN <--
    ) : Exception(message)

    @Serializable
    private data class SyncEvent(
        val event: SyncEventStatus,
        // SY -->
        @SerialName("device_id")
        val deviceId: String? = null,
        // SY <--
        @SerialName("device_name")
        val deviceName: String? = null,
        val message: String? = null,
    )

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    // SY -->
    // KUN -->
    // The deletion request types live at file level so the body builder is testable.
    // KUN <--
    // SY <--

    // SY -->
    /**
     * Protocol v1: download, merge locally, upload the merged library.
     */
    private suspend fun doSyncV1(syncData: SyncData): SyncResult {
        val (remoteData, etag) = pullSyncData()

        val finalSyncData = if (remoteData != null) {
            assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
            logcat(LogPriority.DEBUG, "SyncService") {
                "Try update remote data with ETag($etag)"
            }
            mergeSyncData(syncData, remoteData)
        } else {
            // init or overwrite remote data
            logcat(LogPriority.DEBUG) {
                "Try overwrite remote data with ETag($etag)"
            }
            syncData
        }

        val success = pushSyncData(finalSyncData, etag)

        if (success) {
            reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        } else {
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Failed to push sync data")
            // KMK -->
            // KUN -->
            return SyncResult(null, changed = false, protocolV2 = false)
            // KUN <--
            // KMK <--
        }

        return SyncResult(finalSyncData.backup, changed = remoteData != null, protocolV2 = false)
    }
    // SY <--

    // SY -->
    /**
     * Protocol v2: the server merges. We upload what changed since the last successful sync (or the whole
     * library when [full]) and restore whatever the server says we are missing. No local merge.
     */
    private suspend fun doSyncV2(syncData: SyncData, full: Boolean): SyncResult {
        val backup = syncData.backup ?: return SyncResult(null, changed = false, protocolV2 = true)
        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()

        // SY -->
        submitDeletedItems(host, apiKey)
        // SY <--

        // KUN -->
        // A history entry the user removed is a reset row (`read_at = NULL`). It already travels as
        // an explicit `history` tombstone above; sending the row as `lastRead = 0` as well would
        // lose the server's version-first history merge (0 is never newer) and the live entry would
        // be handed straight back. Only the v2 upload drops it: a manual backup, and the v1/Drive/
        // WebDAV paths that still express a removal that way, keep their rows.
        dropTombstonedHistory(backup.backupManga)
        // KUN <--

        // KUN -->
        // A disabled category sync must not tombstone the server's categories: a force push uploads
        // the server's copy, and an ordinary sync does not manage them either. Report nothing as
        // sent (so the pending set survives) and leave it for a later categories-enabled sync.
        val pendingDeleted = if (syncPreferences.getSyncSettings().categories) {
            syncPreferences.pendingDeletedCategoryUids.get()
        } else {
            emptySet()
        }
        // KUN <--
        val headersBuilder = baseHeaders(apiKey)
            .add("X-Sync-Cursor", syncPreferences.syncCursor.get().toString())
            .add("X-Sync-Full", full.toString())
            .add("Content-Encoding", "gzip")
        if (pendingDeleted.isNotEmpty()) {
            headersBuilder.add("X-Sync-Deleted-Categories", pendingDeleted.joinToString(","))
        }

        val body = BackupRequestBody(backup, protoBuf, gzip = true)
        val request = POST(
            url = "$host/api/sync/v2/merge",
            headers = headersBuilder.build(),
            body = body,
        )

        val response = syncClient().newCall(request).await()

        if (!response.isSuccessful) {
            val responseBody = response.body.string()
            logcat(LogPriority.ERROR) { "SyncError (${response.code}): $responseBody" }
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Server answered ${response.code}")
            // KUN -->
            throw SyncYomiException("Failed to sync: ${response.code} $responseBody", statusCode = response.code)
            // KUN <--
        }

        val bytes = response.body.bytes()
        val remote = protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        val cursor = response.headers["X-Sync-Cursor"]?.toLongOrNull()
            ?: throw SyncYomiException("Missing X-Sync-Cursor")
        val changed = response.headers["X-Sync-Changed"]?.toBoolean() ?: true
        val fullRequested = response.headers["X-Sync-Full-Requested"]?.toBoolean() ?: false

        // KUN -->
        // Defer committing the cursor, clearing pending deletions and moving `lastFullSync` until the
        // restore actually succeeds in SyncManager. A merge alone is not a completed full sync.
        logcat(LogPriority.DEBUG) { "SyncYomi v2 merge done: cursor=$cursor changed=$changed fullRequested=$fullRequested" }

        reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        return SyncResult(
            backup = remote,
            changed = changed,
            protocolV2 = true,
            cursor = cursor,
            pendingDeletedCategories = pendingDeleted,
            fullSyncRequested = fullRequested,
        )
        // KUN <--
    }

    /**
     * A full library is needed on the first sync, when the server asks for it, and once a day as a safety net.
     */
    override suspend fun needsFullSync(): Boolean {
        if (!supportsV2()) return true
        if (syncPreferences.syncCursor.get() == 0L || syncPreferences.fullSyncRequested.get()) return true
        val lastFull = syncPreferences.lastFullSync.get()
        return lastFull == 0L || Clock.System.now() - Instant.fromEpochMilliseconds(lastFull) > FULL_SYNC_INTERVAL
    }

    /**
     * Probes the server once per host for protocol v2 support (`GET /api/sync/v2/capabilities`).
     * 200 means v2, 404 means an old server; anything else is a transient failure and is not cached.
     *
     * KUN -->
     * [forceProbe] bypasses the cached host result for explicit repair actions, so a server that
     * was upgraded in place is detected without waiting for a cache invalidation.
     * KUN <--
     */
    suspend fun supportsV2(
        // KUN -->
        forceProbe: Boolean = false,
        // KUN <--
    ): Boolean {
        val host = syncPreferences.clientHost.get()
        // KUN -->
        if (!forceProbe && syncPreferences.v2ProbedHost.get() == host) {
            return syncPreferences.serverSupportsV2.get()
        }
        // KUN <--

        val request = GET(
            url = "$host/api/sync/v2/capabilities",
            headers = baseHeaders(syncPreferences.clientAPIKey.get()).build(),
        )
        val response = syncClient().newCall(request).await()
        response.close()
        val supported = when (response.code) {
            HttpStatus.SC_OK -> true
            HttpStatus.SC_NOT_FOUND -> false
            else -> throw SyncYomiException("Failed to probe server capabilities: ${response.code}")
        }
        syncPreferences.v2ProbedHost.set(host)
        syncPreferences.serverSupportsV2.set(supported)
        logcat(LogPriority.INFO) { "SyncYomi server supports protocol v2: $supported" }
        return supported
    }

    // KUN -->
    /**
     * A full copy of the server's library, used by a force push to keep the sections the user
     * disabled syncing (history, tracking, categories, custom info) instead of replacing them with
     * empty manga payloads.
     *
     * Null means "the server has nothing to preserve": either 404 (no state stored yet) or a 200
     * with a zero-byte body (an existing but empty `sync_state`, which is how an empty protobuf
     * encodes). `SyncManager` then decides whether that is safe: a never-synced device proceeds,
     * while a previously-synced one aborts. Any other failure throws.
     */
    suspend fun fetchSnapshot(): Backup? {
        val host = syncPreferences.clientHost.get()
        val request = GET(
            url = "$host/api/sync/v2/snapshot",
            headers = baseHeaders(syncPreferences.clientAPIKey.get()).build(),
        )
        val response = syncClient().newCall(request).await()
        response.use { resp ->
            if (resp.code == HttpStatus.SC_NOT_FOUND) return null
            if (!resp.isSuccessful) {
                throw SyncYomiException("Failed to fetch sync snapshot: ${resp.code}")
            }
            val bytes = resp.body.bytes()
            // 404 and an empty protobuf mean the same thing to the caller: no stored data.
            if (bytes.isEmpty()) return null
            return protoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        }
    }
    // KUN <--

    private fun baseHeaders(apiKey: String): Headers.Builder = Headers.Builder()
        .add("X-API-Token", apiKey)
        .add("X-Device-ID", syncPreferences.uniqueDeviceID())
        .add("X-Device-Name", Build.MODEL)
    // SY <--

    // SY -->
    private fun syncClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT)
        .readTimeout(TIMEOUT)
        .writeTimeout(TIMEOUT)
        .build()
    // SY <--

    // SY -->
    override suspend fun doSync(syncData: SyncData, full: Boolean): SyncResult {
        reportSyncEvent(SyncEventStatus.SYNC_STARTED)

        var v2 = false
        try {
            v2 = supportsV2()
            return if (v2) {
                doSyncV2(syncData, full)
            } else {
                doSyncV1(syncData)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, e.message)
            // KUN -->
            return SyncResult(
                null,
                changed = false,
                protocolV2 = v2,
                failureStatus = (e as? SyncYomiException)?.statusCode,
            )
            // KUN <--
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.ERROR) { "Out of memory while syncing" }
            notifier.showSyncError("Not enough memory to sync this library")
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, "OutOfMemoryError")
            // KUN -->
            // A forced full upload that runs out of memory will do it again; a later delta may fit.
            return SyncResult(null, changed = false, protocolV2 = v2, permanentFailure = true)
            // KUN <--
        }
    }
    // SY <--

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey)
        val lastETag = syncPreferences.lastSyncEtag.get()
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest = GET(
            url = downloadUrl,
            headers = headers,
        )

        val response = client.newCall(downloadRequest).await()

        if (response.code == HttpStatus.SC_NOT_MODIFIED) {
            // not modified
            assert(lastETag.isNotEmpty())
            logcat(LogPriority.INFO) {
                "Remote server not modified"
            }
            return Pair(null, lastETag)
        } else if (response.code == HttpStatus.SC_NOT_FOUND) {
            // maybe got deleted from remote
            return Pair(null, "")
        }

        if (response.isSuccessful) {
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")

            val byteArray = response.body.bytes()

            return try {
                val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (_: SerializationException) {
                logcat(LogPriority.INFO) {
                    "Bad content responsed from server"
                }
                // the body is invalid
                // return default value so we can overwrite it
                Pair(null, "")
            }
        } else {
            val responseBody = response.body.string()
            notifier.showSyncError("Failed to download sync data: $responseBody")
            logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    /**
     * Return true if update success
     */
    private suspend fun pushSyncData(syncData: SyncData, eTag: String): Boolean {
        val backup = syncData.backup ?: return true

        val host = syncPreferences.clientHost.get()
        val apiKey = syncPreferences.clientAPIKey.get()
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey).add("Content-Encoding", "gzip")
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        // SY -->
        val body = BackupRequestBody(backup, protoBuf, gzip = true)
        if (body.metaBytes.isEmpty() && backup.backupManga.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }
        // SY <--

        val uploadRequest = PUT(
            url = uploadUrl,
            headers = headers,
            body = body,
        )

        client.newCall(uploadRequest).await()
            // KMK -->
            .use { response ->
                // KMK <--
                if (response.isSuccessful) {
                    val newETag = response.headers["ETag"]
                        .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
                    syncPreferences.lastSyncEtag.set(newETag)
                    logcat(LogPriority.DEBUG) { "SyncYomi sync completed" }
                    return true
                } else if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
                    // other clients updated remote data, will try next time
                    logcat(LogPriority.DEBUG) { "SyncYomi sync failed with 412" }
                    return false
                } else {
                    val responseBody = response.body.string()
                    notifier.showSyncError("Failed to upload sync data: $responseBody")
                    logcat(LogPriority.ERROR) { "SyncError: $responseBody" }
                    return false
                }
            }
    }

    private suspend fun reportSyncEvent(event: SyncEventStatus, message: String? = null) {
        withContext(NonCancellable) {
            try {
                val host = syncPreferences.clientHost.get()
                val apiKey = syncPreferences.clientAPIKey.get()
                val url = "$host/api/sync/event"

                val headers = baseHeaders(apiKey).build()

                val bodyObj = SyncEvent(
                    event = event,
                    // SY -->
                    deviceId = syncPreferences.uniqueDeviceID(),
                    // SY <--
                    deviceName = Build.MODEL,
                    message = message,
                )

                val jsonBody = json.encodeToString(SyncEvent.serializer(), bodyObj)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = POST(
                    url = url,
                    headers = headers,
                    body = requestBody,
                )

                client.newCall(request).await().close()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to report sync event: ${e.message}" }
            }
        }
    }

    // KUN -->
    /**
     * Reports the chapter and history entries a source or the user dropped, and the manga deleted
     * locally, to the v2 deletions endpoint before the merge, so the server tombstones them.
     *
     * Keys are cleared only on a 2xx, so an older server without the route (404) loses nothing and
     * they go out once it is upgraded. Chapter keys are also honoured by the legacy request shape;
     * manga and history keys are only cleared once the response reports `applied`, because a server
     * that predates the `items` shape ignores them while still answering 200.
     *
     * The history tombstones come from the database (`history.pending_deletion`) and go out in pages
     * of [HISTORY_DELETION_BATCH], so "clear all history" never builds one unbounded request. Each
     * page is cleared only on its own confirmation; a server that answers without applying the
     * `items` shape ends the run, since it would ignore every later page too, and the queue is left
     * for the next sync.
     */
    // KUN <--
    // SY -->
    private suspend fun submitDeletedItems(host: String, apiKey: String) {
        // KUN -->
        val syncSettings = syncPreferences.getSyncSettings()
        // A device that does not sync a section does not manage the server's rows for it either, so
        // that section's tombstones stay pending instead of deleting what it never uploaded.
        val historiesEnabled = syncSettings.history
        val mangas = if (syncSettings.libraryEntries) {
            syncPreferences.pendingDeletedMangaKeys.get()
        } else {
            emptySet()
        }
        // KUN <--
        val chapters = syncPreferences.pendingDeletedChapterKeys.get()

        // KUN -->
        // The chapter and manga keys ride on the first request, since they have no cursor: they are
        // a set that is either acknowledged or not. The history queue is paged by row id.
        var firstBatch = true
        var afterId = 0L
        while (true) {
            val historyRows = if (historiesEnabled) {
                historyRepository.getPendingHistoryDeletions(HISTORY_DELETION_BATCH, afterId)
            } else {
                emptyList()
            }
            val batchChapters = if (firstBatch) chapters else emptySet()
            val batchMangas = if (firstBatch) mangas else emptySet()
            if (historyRows.isEmpty() && batchChapters.isEmpty() && batchMangas.isEmpty()) return

            val historyKeys = historyDeletionKeys(historyRows)
            val responseBody = postDeletions(host, apiKey, batchChapters, batchMangas, historyKeys)
                // A transport failure or a non-2xx status: nothing was confirmed, so every key of
                // this and every later batch stays pending for the next run.
                ?: return
            // Only the keys actually sent: one recorded while the request was in flight stays
            // pending for the next run. A pre-#335 server answers 200 and ignores `items`, so the
            // kinds that have no legacy shape are only cleared when the response says it applied
            // them.
            val acknowledged = acknowledgedDeletions(responseBody, batchChapters, batchMangas, historyKeys)
            syncPreferences.forgetDeletedChapters(acknowledged.chapters)
            if (acknowledged.mangas.isNotEmpty()) syncPreferences.forgetDeletedManga(acknowledged.mangas)
            if (batchMangas.isNotEmpty() && acknowledged.mangas.isEmpty()) {
                logcat(LogPriority.DEBUG) {
                    "SyncYomi v2 deletions: manga tombstones stay pending, " +
                        "the server did not report `applied`"
                }
            }
            // A server that did not report `applied` ignored every `items` entry, not just this
            // page, so the rest of the queue cannot be applied in this run either: keep it queued
            // for the next sync instead of spending one request per page on a server that answers
            // without applying anything.
            if (historyKeys.isNotEmpty() && acknowledged.histories.isEmpty()) {
                logcat(LogPriority.DEBUG) {
                    "SyncYomi v2 deletions: ${historyKeys.size} history tombstones stay pending, " +
                        "the server did not report `applied`"
                }
                return
            }
            // The queue is the row, so clearing it is a write: only the rows this page carried, and
            // only once the server reported `applied`.
            if (historyRows.isNotEmpty()) {
                historyRepository.clearPendingHistoryDeletion(historyRows.map { it.historyId })
            }
            logcat(LogPriority.DEBUG) {
                "SyncYomi v2 deletions applied: ${batchChapters.size} chapters, " +
                    "${historyKeys.size} history, ${batchMangas.size} manga"
            }

            // Nothing further to page through.
            if (historyRows.isEmpty()) return
            firstBatch = false
            afterId = historyRows.last().historyId
        }
    }

    /**
     * Sends one deletions request. Returns the 2xx response body, or null when the answer is not a
     * confirmation (transport failure or a non-2xx status).
     */
    private suspend fun postDeletions(
        host: String,
        apiKey: String,
        chapters: Collection<String>,
        mangas: Collection<String>,
        histories: Collection<String>,
    ): String? {
        val request = POST(
            url = "$host/api/sync/v2/deletions",
            headers = baseHeaders(apiKey).build(),
            body = json
                .encodeToString(
                    DeletionRequest.serializer(),
                    DeletionRequest(
                        items = deletionItems(chapters, mangas, histories),
                        // An older server only reads the legacy shape; it merges and dedupes both.
                        deletedChapters = chapters.toList(),
                    ),
                )
                .toRequestBody("application/json; charset=utf-8".toMediaType()),
        )

        val response = try {
            syncClient().newCall(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "SyncYomi v2 deletions not sent: ${e.message}" }
            return null
        }
        // The body is read, not just the status: a server that ignores `items` still answers 200.
        // An unreadable body is not a confirmation either, so a failure here keeps every key pending
        // for the next run instead of aborting the sync.
        return response.use { resp ->
            if (!resp.isSuccessful) {
                logcat(LogPriority.WARN) { "SyncYomi v2 deletions not applied: ${resp.code}" }
                return null
            }
            try {
                resp.body.string()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "SyncYomi v2 deletions response unreadable: ${e.message}" }
                null
            }
        }
    }
    // KUN <--
    // SY <--
}

// KUN -->
/**
 * History tombstones per deletions request. The endpoint takes a list and "clear all history" can
 * queue a whole library, so the queue is sent in bounded pages: 500 keys is a few tens of kB of
 * JSON, small enough for any body limit the merge endpoint tolerates.
 */
internal const val HISTORY_DELETION_BATCH = 500

/** The `history` tombstone keys of [rows]: the server keys a history entry by its chapter. */
internal fun historyDeletionKeys(rows: List<PendingHistoryDeletion>): List<String> =
    rows.map { chapterTombstoneKey(it.sourceId, it.mangaUrl, it.chapterUrl) }

/** One entry of the SyncYomi v2 deletions request; [key] is a chapter or manga tombstone key. */
@Serializable
internal data class DeletionItem(val kind: String, val key: String)

/**
 * The body of `POST /api/sync/v2/deletions`.
 *
 * [items] is the shape current servers read. [deletedChapters] is the legacy shape an older server
 * still understands; it carries the chapter keys only, and a server that reads both merges and
 * dedupes them. Sending both during the transition means an old server that 2xxes and ignores
 * `items` still applies the chapter tombstones before the client clears its queue.
 */
@Serializable
internal data class DeletionRequest(
    val items: List<DeletionItem> = emptyList(),
    val deletedChapters: List<String> = emptyList(),
)

// Chapters go first: a manga tombstone removes the row, so the server must see the chapter keys
// before it forgets the manga they belong to. History entries go with the chapters: they are keyed
// by a chapter too, and a manga tombstone takes them with it.
internal fun deletionItems(
    chapters: Collection<String>,
    mangas: Collection<String>,
    histories: Collection<String> = emptyList(),
): List<DeletionItem> =
    chapters.map { DeletionItem("chapter", it) } +
        histories.map { DeletionItem("history", it) } +
        mangas.map { DeletionItem("manga", it) }

/**
 * The body of a 2xx `POST /api/sync/v2/deletions`. It carries **counts, not keys**: `applied` counts
 * the entries the server actually changed and only exists on a server that reads the `items` shape;
 * an older one answers `{"acknowledged":n}` after processing the legacy `deletedChapters` list alone.
 * `acknowledged` counts the entries the server read, so a batch it deduplicated to no change
 * (`applied: 0`) still reports the full entry count.
 */
@Serializable
internal data class DeletionResponse(
    val acknowledged: Long = 0,
    val applied: Long? = null,
)

private val deletionResponseJson = Json { ignoreUnknownKeys = true }

/**
 * Whether the response confirms that the server processed **every** entry of a request that carried
 * [itemCount] items.
 *
 * The presence of `applied` only says the server understands the `items` shape; it says nothing
 * about how much of this request it processed, and a partially applied page (or one the server
 * deduplicated against entries it already held) must keep its tombstones. Because the body has no
 * per-key answer, the count is the only evidence available: every entry has to be accounted for,
 * either as a changed row (`applied`) or as a read one (`acknowledged`). A retry against entries the
 * server already tombstoned answers `applied: 0` with the full `acknowledged`, so it still clears.
 */
internal fun deletionResponseCoversEveryItem(body: String?, itemCount: Int): Boolean {
    if (body.isNullOrBlank() || itemCount <= 0) return false
    val response = try {
        deletionResponseJson.decodeFromString(DeletionResponse.serializer(), body)
    } catch (_: IllegalArgumentException) {
        // not JSON, or not the shape above: an older server, so no confirmation
        return false
    }
    // no `applied` at all: a server that only read the legacy `deletedChapters` list
    val applied = response.applied ?: return false
    return maxOf(response.acknowledged, applied) >= itemCount
}

/** The pending keys a 2xx deletions response lets this device forget. */
internal data class AcknowledgedDeletions(
    val chapters: Collection<String>,
    val mangas: Collection<String>,
    val histories: Collection<String>,
)

/**
 * Splits the keys a deletions request carried into the ones the response acknowledged.
 *
 * Chapter keys are safe to clear on any server: the legacy `deletedChapters` shape is still honoured,
 * so even a server that ignores `items` applied them. Manga and history keys have no legacy shape
 * and no per-key answer either, so they are cleared only when the response accounts for every entry
 * of the request — a page the server applied only in part keeps them for the next run.
 */
internal fun acknowledgedDeletions(
    responseBody: String?,
    chapters: Collection<String>,
    mangas: Collection<String>,
    histories: Collection<String>,
): AcknowledgedDeletions {
    // the request carries exactly one item per key (the legacy chapter list repeats its own keys)
    val itemCount = chapters.size + mangas.size + histories.size
    return if (deletionResponseCoversEveryItem(responseBody, itemCount)) {
        AcknowledgedDeletions(chapters, mangas, histories)
    } else {
        AcknowledgedDeletions(chapters, emptySet(), emptySet())
    }
}
// KUN <--
