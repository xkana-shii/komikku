package tachiyomi.domain.debug.repository

/**
 * Maintenance operations exposed by the hidden debug settings screen. None of these are part of a
 * user-facing flow; they exist so `DebugFunctions` does not have to reach into the generated
 * `tachiyomi.data.Database`.
 */
interface DebugRepository {

    /** Marks every manga in the database as a library entry. */
    suspend fun addAllMangaToLibrary()

    /** Undoes the old reader-viewer backup corruption (`user_reader_flags = -1`). */
    suspend fun fixReaderViewerBackupBug()

    /** Resets the reader viewer flags of every manga. */
    suspend fun resetReaderViewerForAllManga()

    /** Moves every library manga of a language-specific NHentai source to [newSourceId]. */
    suspend fun migrateAllNhentaiToOtherLang(newSourceId: Long, oldSourceIds: List<Long>)
}
