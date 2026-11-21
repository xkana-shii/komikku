package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.sync.mangaTombstoneKey
import tachiyomi.domain.sync.MangaTombstoneRecorder

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncMangaTombstoneRecorder(
    private val syncPreferences: SyncPreferences,
) : MangaTombstoneRecorder {
    override fun rememberDeletedManga(sourceId: Long, mangaUrl: String) {
        syncPreferences.rememberDeletedManga(listOf(mangaTombstoneKey(sourceId, mangaUrl)))
    }
}
