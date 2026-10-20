package tachiyomi.domain.backup.repository

import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

interface RestoreRepository {

    suspend fun getMangaUrlsBySourceId(): Map<Long, Set<String>>

    /**
     * Restores [entries] all together: either every one of them is restored or none is. [update] is what to change on each
     * entry once its chapters and history are in place, and is part of the same all-or-nothing restore.
     */
    suspend fun restoreManga(
        entries: List<RestoredManga>,
        // KMK/SY -->
        // Whether this restore is applying a SyncYomi sync delta. A sync must not stamp the restored
        // rows as local changes, so the raw inserts/updates go through with is_syncing set.
        isSync: Boolean,
        // KMK/SY <--
        // KUN -->
        restoreCategories: Boolean = true,
        // Section flags for the manga payload. A disabled section must not be read or written at
        // all, so it performs zero writes and leaves versions/timestamps untouched.
        restoreTracking: Boolean = true,
        restoreHistory: Boolean = true,
        restoreCustomInfo: Boolean = true,
        // Epoch seconds of the upload snapshot, or null to leave local chapters alone. When set,
        // the incoming chapter list is authoritative as of that instant: local chapters missing
        // from it are deleted, except chapters created at or after the snapshot. An empty list is
        // authoritative too (every chapter was deleted), so the caller must only set this for a
        // complete SyncYomi v2 response. The same snapshot is the cutoff for history removals.
        removeMissingChaptersBefore: Long? = null,
        // KUN <--
        update: suspend (Manga) -> MangaUpdate,
    )

    // KMK/SY -->
    /**
     * Writes [manga] with the backup/sync update query. Used by SyncManager to reconcile the favorite
     * status of non-library entries without stamping them as locally changed.
     */
    suspend fun updateManga(manga: Manga, isSync: Boolean)
    // KMK/SY <--
}
