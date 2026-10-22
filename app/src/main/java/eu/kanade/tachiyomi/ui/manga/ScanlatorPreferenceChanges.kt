package eu.kanade.tachiyomi.ui.manga

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.Change
import tachiyomi.domain.chapter.model.encodeScanlatorRangeRule
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaUpdate

// KNS -->
/**
 * Stores one edit of the scanlator preferences dialog through the existing persistence: priority,
 * Smart Merge override, range rules and blacklist on the manga, exclusions in `excluded_scanlator`.
 * Each change writes only its own field. A blacklist removal starts from the stored blacklist, so a
 * removal right after another (before the screen state caught up) does not bring the first back.
 */
@Inject
class ScanlatorPreferenceChanges(
    private val updateManga: UpdateManga,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val getManga: GetManga,
) {
    suspend fun apply(mangaId: Long, change: Change) {
        when (change) {
            is Change.Priority -> updateManga.await(MangaUpdate(mangaId) { scanlatorPriority = change.order })
            is Change.SmartMerge -> updateManga.await(MangaUpdate(mangaId) { smartMergeOverride = change.override })
            is Change.RangeRules -> updateManga.await(
                MangaUpdate(mangaId) { scanlatorRangeRules = change.rules.map(::encodeScanlatorRangeRule) },
            )
            is Change.Excluded -> setExcludedScanlators.await(mangaId, change.excluded)
            is Change.RemoveBlacklistEntries -> {
                val stored = getManga.await(mangaId)?.blacklistedChapters ?: return
                val remaining = stored.filterNot(change.keys::contains)
                if (remaining != stored) updateManga.await(MangaUpdate(mangaId) { blacklistedChapters = remaining })
            }
        }
    }
}
// KNS <--
