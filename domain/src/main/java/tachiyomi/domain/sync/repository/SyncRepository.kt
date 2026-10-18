package tachiyomi.domain.sync.repository

import tachiyomi.domain.manga.model.Manga

/**
 * Data access for the SyncYomi sync window: the manga rows that must be uploaded and the
 * `is_syncing` flag reset that has to happen before a sync writes.
 *
 * These queries used to be issued by `SyncManager` against the generated `Database`
 * directly; keeping them here means the app module no longer reaches into `tachiyomi.data`.
 */
interface SyncRepository {

    /**
     * Clears the `is_syncing` flag on manga, chapter and category in a single transaction. A
     * previous sync run can leave rows flagged if it was interrupted before its restore.
     */
    suspend fun resetSyncing()

    /**
     * Every manga that has ever been favorited (i.e. has a favorite timestamp), for a full upload.
     */
    suspend fun getMangasWithFavoriteTimestamp(): List<Manga>

    /** Every manga, used by the sync to reconcile remote entries against the local library. */
    suspend fun getAllManga(): List<Manga>

    /**
     * Every manga (or with a chapter) modified at or after [since], for a delta upload.
     */
    suspend fun getMangasModifiedSince(since: Long): List<Manga>
}
