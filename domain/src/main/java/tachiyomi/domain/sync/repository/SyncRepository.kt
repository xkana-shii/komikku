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

    // KUN -->
    /**
     * One-shot repair for a stale device: raises the sync version of every library manga, its
     * chapters and (when [bumpCategories]) every user category to at least [version] in a single
     * transaction, and moves their `last_modified_at` to at least [lastModifiedAt]. This makes the
     * local state authoritative for the upload without bumping rows that are not part of the
     * library. The timestamps only move forward, so an edit made while the forced upload was in
     * flight keeps its newer value for the next delta.
     *
     * Callers run this only after the server accepted the forced upload, so an interrupted repair
     * cannot leave epoch versions behind for an ordinary sync to upload with empty sections. A
     * disabled category sync uploads the server's copy instead, hence [bumpCategories].
     */
    suspend fun markForForcePush(version: Long, lastModifiedAt: Long, bumpCategories: Boolean)
    // KUN <--
}
