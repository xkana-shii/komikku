package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.Change
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.chapter.model.encodeScanlatorRangeRule
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.i18n.kmk.KMR

// KNS -->
/**
 * Stores one edit of the scanlator preferences dialog through the existing persistence: priority,
 * Smart Merge override, range rules and blacklist on the manga, exclusions in `excluded_scanlator`.
 * Each change writes only its own field. A blacklist removal starts from the stored blacklist, so a
 * removal right after another (before the screen state caught up) does not bring the first back; a
 * new priority keeps the stored positions of scanlators the dialog did not list ([mergePriority]).
 * A write the database rejects throws, so it is never reported as stored.
 */
@Inject
class ScanlatorPreferenceChanges(
    private val updateManga: UpdateManga,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val getManga: GetManga,
) {
    suspend fun apply(mangaId: Long, change: Change) {
        when (change) {
            is Change.Priority -> {
                val stored = getManga.await(mangaId)?.scanlatorPriority.orEmpty()
                update(MangaUpdate(mangaId) { scanlatorPriority = mergePriority(stored, change.order) })
            }
            // Null (follow the global setting) is written like the other two values.
            is Change.SmartMerge -> update(MangaUpdate(mangaId) { smartMergeOverride = change.override })
            is Change.RangeRules -> update(
                MangaUpdate(mangaId) { scanlatorRangeRules = change.rules.map(::encodeScanlatorRangeRule) },
            )
            is Change.Excluded -> setExcludedScanlators.await(mangaId, change.excluded)
            is Change.RemoveBlacklistEntries -> {
                val stored = getManga.await(mangaId)?.blacklistedChapters ?: return
                val remaining = stored.filterNot(change.keys::contains)
                if (remaining != stored) update(MangaUpdate(mangaId) { blacklistedChapters = remaining })
            }
        }
    }

    private suspend fun update(update: MangaUpdate) {
        check(updateManga.await(update)) { "The manga ${update.id} could not be updated" }
    }

    companion object {
        /**
         * The priority to store when the dialog lists [edited], the order of the scanlators it showed:
         * those take the edited order, and each stored scanlator the dialog did not list (absent from the
         * current chapters, e.g. during a refresh) keeps its position in the list, so it is back at its
         * place when it reappears. Duplicates are dropped; names are compared exactly, as the chapters and
         * Smart Merge compare them.
         */
        fun mergePriority(stored: List<String>, edited: List<String>): List<String> {
            val shown = edited.distinct()
            val shownSet = shown.toSet()
            val result = shown.toMutableList()
            stored.distinct().forEachIndexed { position, name ->
                if (name !in shownSet && name !in result) result.add(position.coerceAtMost(result.size), name)
            }
            return result
        }
    }
}

/**
 * The one queue every scanlator preferences dialog writes through. It lives on the app scope, so an edit
 * is stored even when the dialog is closed and the entry left right after it, and it is app-wide, so a
 * dialog opened again (also on a new screen) reads the stored values only after earlier edits landed.
 */
@Inject
@SingleIn(AppScope::class)
class ScanlatorPreferenceWriter(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val changes: ScanlatorPreferenceChanges,
) {
    private val writer = SequentialWriter<Pair<Long, Change>>(
        scope = appScope,
        onError = { (_, change), e ->
            xLogE("Could not store the scanlator preference $change", e)
            appScope.launch(Dispatchers.Main) { context.toast(KMR.strings.scanlator_preferences_not_saved) }
        },
    ) { (mangaId, change) -> changes.apply(mangaId, change) }

    fun submit(mangaId: Long, change: Change) = writer.submit(mangaId to change)

    /** Waits until every submitted edit has been stored (or has failed). */
    suspend fun awaitIdle() = writer.awaitIdle()
}
// KNS <--
