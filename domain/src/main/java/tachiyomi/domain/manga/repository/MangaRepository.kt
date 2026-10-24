package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getFavorites(): List<Manga>

    suspend fun getReadMangaNotInLibrary(): List<Manga>

    suspend fun getLibraryManga(): List<LibraryManga>

    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>>

    suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount>

    suspend fun getUpcomingManga(
        statuses: Set<Long>,
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Manga>>

    suspend fun resetViewerFlags(): Boolean

    suspend fun deleteNonLibraryManga(sourceIds: List<Long>, keepReadManga: Boolean)

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun getExcludedScanlators(mangaId: Long): Set<String>

    suspend fun getExcludedScanlatorsByMangaIds(mangaIds: List<Long>): Map<Long, List<String>>

    fun getExcludedScanlatorsAsFlow(mangaId: Long): Flow<Set<String>>

    suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>)

    suspend fun update(update: MangaUpdate): Boolean

    suspend fun updateRemote(update: MangaRemoteUpdate): Boolean

    // KUN -->
    /**
     * Updates only the source-owned cover URL of [mangaId], leaving every other field untouched.
     * The write only succeeds while the entry still points at [staleUrl], the new URL differs, and
     * no sync is applying (`is_syncing = 0`); otherwise it fails so the recovery can be retried
     * after sync. It bumps `version` explicitly, since routine cover-only changes no longer do; the
     * SyncYomi triggers still stamp `last_modified_at`, so the new URL is uploaded.
     *
     * Syncing is best-effort: the recovered cover only wins when its version is strictly higher
     * than a peer's. Equal or higher peer versions keep the peer row. A cover-specific version or
     * reconciler is a follow-up.
     */
    suspend fun updateCover(mangaId: Long, staleUrl: String, thumbnailUrl: String): Boolean

    /**
     * Whether a sync is currently applying to [mangaId]. Used to avoid racing a recovery write.
     */
    suspend fun isSyncing(mangaId: Long): Boolean

    /**
     * Bumps `version` and `last_modified_at` of [mangaId] so the next sync delta uploads the manga.
     * Used by user actions that change data stored outside the manga row (history, custom info), which
     * the sync triggers cannot see. It is a no-op while a sync is applying (`is_syncing != 0`).
     */
    suspend fun bumpSyncVersion(mangaId: Long)
    // KUN <--

    suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean

    suspend fun insertNetworkManga(
        manga: List<Manga>,
        // KMK -->
        updateInfo: Boolean = true,
        // KMK <--
    ): List<Manga>

    // SY -->
    suspend fun getMangaBySourceId(sourceId: Long): List<Manga>

    suspend fun getAll(): List<Manga>

    suspend fun deleteManga(mangaId: Long)

    suspend fun getReadMangaNotInLibraryView(): List<LibraryManga>

    suspend fun updateUserInfo(
        mangaId: Long,
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        genre: List<String>?,
        status: Long?,
    )

    suspend fun updateUserInfoAll(updates: List<MangaUserInfoUpdate>)
    // SY <--
}
