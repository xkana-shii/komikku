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
