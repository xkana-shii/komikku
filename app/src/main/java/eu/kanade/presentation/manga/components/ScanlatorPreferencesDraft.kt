package eu.kanade.presentation.manga.components

import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.parseScanlatorRangeRule

// KNS -->
/**
 * The unsaved state of the scanlator preferences dialog. Both of its modes (scanlator preferences
 * and chapter blacklist) edit this one draft, so switching modes never loses a change; nothing is
 * stored until the dialog is saved.
 */
data class ScanlatorPreferencesDraft(
    val mode: Mode = Mode.Scanlators,
    /** Every available scanlator, most preferred first. */
    val order: List<String>,
    /** Excluded scanlators (also ones that are not available right now, which stay excluded). */
    val excluded: Set<String>,
    val smartMergeOverride: Boolean?,
    val rangeRules: List<ScanlatorRangeRule>,
    val blacklist: List<BlacklistItem>,
    val removedBlacklistKeys: Set<String> = emptySet(),
    val rangeInput: RangeInput = RangeInput(),
) {
    enum class Mode { Scanlators, Blacklist }

    /** A blacklist entry and the names of the chapters it currently hides (empty = unavailable). */
    data class BlacklistItem(val key: String, val chapters: List<String>)

    /** The chapter-range form, kept here so a half-typed range survives a mode switch. */
    data class RangeInput(val from: String = "", val to: String = "", val scanlator: String? = null)

    val visibleBlacklist: List<BlacklistItem>
        get() = blacklist.filterNot { it.key in removedBlacklistKeys }

    fun switchMode(): ScanlatorPreferencesDraft =
        copy(mode = if (mode == Mode.Scanlators) Mode.Blacklist else Mode.Scanlators)

    /** Moves the scanlator at [from] to [to], as a drag in the priority list does. */
    fun move(from: Int, to: Int): ScanlatorPreferencesDraft {
        if (from !in order.indices || to !in order.indices || from == to) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toggleExcluded(scanlator: String): ScanlatorPreferencesDraft =
        copy(excluded = if (scanlator in excluded) excluded - scanlator else excluded + scanlator)

    fun isExcluded(scanlator: String): Boolean = scanlator in excluded

    /** The scanlator the range form adds a rule for: the chosen one, else the most preferred. */
    val rangeScanlator: String?
        get() = rangeInput.scanlator?.takeIf { it in order } ?: order.firstOrNull()

    /** The rule the range form describes, or null while it is incomplete or invalid. */
    val pendingRangeRule: ScanlatorRangeRule?
        get() {
            val scanlator = rangeScanlator ?: return null
            return parseScanlatorRangeRule("${rangeInput.from.trim()}:${rangeInput.to.trim()}:$scanlator")
        }

    /** Adds the range form's rule (after the existing ones, so it wins where they overlap). */
    fun addPendingRangeRule(): ScanlatorPreferencesDraft {
        val rule = pendingRangeRule ?: return this
        return copy(rangeRules = rangeRules + rule, rangeInput = rangeInput.copy(from = "", to = ""))
    }

    fun removeRangeRule(index: Int): ScanlatorPreferencesDraft =
        if (index !in rangeRules.indices) this else copy(rangeRules = rangeRules.filterIndexed { i, _ -> i != index })

    fun removeBlacklistEntry(key: String): ScanlatorPreferencesDraft =
        copy(removedBlacklistKeys = removedBlacklistKeys + key)

    companion object {
        /**
         * A draft of the saved preferences: the saved priority first (scanlators that are no longer
         * available are left out), then the scanlators without a priority alphabetically.
         */
        fun create(
            scanlators: Collection<String>,
            priority: List<String>,
            excluded: Set<String>,
            smartMergeOverride: Boolean?,
            rangeRules: List<ScanlatorRangeRule>,
            blacklist: List<BlacklistItem>,
        ): ScanlatorPreferencesDraft {
            val available = scanlators.toSet()
            val prioritized = priority.filter { it in available }.distinct()
            val rest = available.filterNot { it in prioritized }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            return ScanlatorPreferencesDraft(
                order = prioritized + rest,
                excluded = excluded,
                smartMergeOverride = smartMergeOverride,
                rangeRules = rangeRules,
                blacklist = blacklist,
            )
        }
    }
}
// KNS <--
