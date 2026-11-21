package tachiyomi.domain.sync

/** Records a locally hard-deleted manga for the next SyncYomi v2 deletion upload. */
fun interface MangaTombstoneRecorder {
    fun rememberDeletedManga(sourceId: Long, mangaUrl: String)
}
