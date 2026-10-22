// KUN -->
package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.sync.models.SyncSettings
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Bumps the sync state of manga whose user-visible change lives outside the manga row.
 *
 * History resets write `read_at = NULL` and custom-info edits go to a JSON file, so none of the
 * manga sync triggers fire and the delta sync would never upload them. The bump is what makes the
 * next upload carry the manga (and therefore the section) again.
 *
 * The bump is gated on the sync service being enabled *and* on the matching "choose what to sync"
 * flag. Uploading a manga while a section is disabled would send that section's local (possibly
 * empty or stale) content and let it win by version on the server, so a disabled section must stay
 * completely untouched.
 */
@Inject
@SingleIn(AppScope::class)
class SyncStateBumper(
    private val syncPreferences: SyncPreferences,
    private val mangaRepository: MangaRepository,
) {

    /** Bumps every manga whose history was reset/tombstoned by the user. */
    suspend fun bumpForHistoryRemoval(mangaIds: Collection<Long>) {
        if (mangaIds.isEmpty() || !isEnabled { it.history }) return
        mangaIds.distinct().forEach { mangaRepository.bumpSyncVersion(it) }
    }

    /**
     * Bumps [mangaId] when a custom-info edit actually changed the stored overrides. [before] is the
     * value returned by `GetCustomMangaInfo.get` before the edit, [after] the value that was written.
     *
     * Only user edits call this: the restore path writes custom info without bumping, because a
     * restore must not echo the applied payload back as a new local edit.
     */
    suspend fun bumpForCustomInfoEdit(
        mangaId: Long,
        before: CustomMangaInfo?,
        after: CustomMangaInfo,
    ) {
        if (!isEnabled { it.customInfo }) return
        if (before.normalized() == after.normalized()) return
        mangaRepository.bumpSyncVersion(mangaId)
    }

    private inline fun isEnabled(section: (SyncSettings) -> Boolean): Boolean {
        if (!syncPreferences.isSyncEnabled()) return false
        return section(syncPreferences.getSyncSettings())
    }

    /**
     * Mirrors how [tachiyomi.domain.manga.repository.CustomMangaRepository] stores an entry: a blank
     * title and a zero status are not stored as overrides, and an entry with no override at all is
     * removed instead of written. Without this, clearing an override to `null`/blank would compare
     * unequal to the stored `null` and bump the manga for a no-op.
     */
    private fun CustomMangaInfo?.normalized(): CustomMangaInfo? {
        val info = this ?: return null
        val normalized = info.copy(
            title = info.title?.takeUnless { it.isBlank() },
            status = info.status?.takeUnless { it == 0L },
        )
        val hasOverride = normalized.title != null ||
            normalized.author != null ||
            normalized.artist != null ||
            normalized.thumbnailUrl != null ||
            normalized.description != null ||
            normalized.genre != null ||
            normalized.status != null
        return normalized.takeIf { hasOverride }
    }
}
// KUN <--
