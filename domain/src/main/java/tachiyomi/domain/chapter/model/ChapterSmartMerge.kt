package tachiyomi.domain.chapter.model

import tachiyomi.domain.chapter.service.filterBlacklistedChapters
import tachiyomi.domain.manga.model.Manga

// KNS -->
private const val TENTHS = 10L

data class ScanlatorRangeRule(
    val from: Double,
    val to: Double,
    val scanlator: String,
) {
    fun covers(chapterNumber: Double): Boolean = chapterNumber >= from && chapterNumber <= to
}

fun encodeScanlatorRangeRule(rule: ScanlatorRangeRule): String = "${rule.from}:${rule.to}:${rule.scanlator}"

fun parseScanlatorRangeRule(raw: String): ScanlatorRangeRule? {
    val parts = raw.split(':', limit = 3)
    if (parts.size != 3) return null

    val from = parts[0].toDoubleOrNull() ?: return null
    val to = parts[1].toDoubleOrNull() ?: return null
    if (!from.isFinite() || !to.isFinite() || from > to) return null

    return ScanlatorRangeRule(from, to, parts[2])
}

/**
 * Removes blacklisted chapters and, when configured, keeps one chapter for each valid positive
 * number. Range rules take priority over the global scanlator order. Source order is retained
 * whenever candidates have the same preference.
 */
fun List<Chapter>.applySmartScanlatorMerge(
    priority: List<String>,
    blacklistedChapters: Collection<String>,
    rangeRules: List<String>,
): List<Chapter> {
    val visible = filterBlacklistedChapters(this, blacklistedChapters, this)
    val parsedRules = rangeRules.mapNotNull(::parseScanlatorRangeRule)
    val selectedIds = visible.asSequence()
        .filter { it.chapterNumber.isFinite() && it.chapterNumber > 0.0 }
        .groupBy(Chapter::chapterNumber)
        .values
        .mapNotNull { candidates ->
            val number = candidates.first().chapterNumber
            val preferredScanlator = parsedRules.lastOrNull { it.covers(number) }?.scanlator
            candidates.minWithOrNull(
                compareBy<Chapter> { candidate ->
                    if (candidate.scanlator == preferredScanlator) 0 else 1
                }.thenBy { candidate ->
                    priority.indexOf(candidate.scanlator)
                        .takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                },
            )?.id
        }
        .toSet()

    return visible.filter { chapter ->
        !chapter.chapterNumber.isFinite() || chapter.chapterNumber <= 0.0 || chapter.id in selectedIds
    }
}

/** The manga's Smart Scanlator Merge setting: its own override, else the global [globalEnabled]. */
fun Manga.smartScanlatorMergeEnabled(globalEnabled: Boolean): Boolean = smartMergeOverride ?: globalEnabled

/** Applies the same chapter visibility rule to every user-facing chapter sequence. */
fun List<Chapter>.filterChapterVisibility(
    manga: Manga,
    smartScanlatorMergeEnabled: Boolean,
    allChapters: List<Chapter> = this,
): List<Chapter> {
    val unblacklisted = filterBlacklistedChapters(this, manga.blacklistedChapters, allChapters)
    return if (smartScanlatorMergeEnabled) {
        unblacklisted.applySmartScanlatorMerge(
            priority = manga.scanlatorPriority,
            blacklistedChapters = emptyList(),
            rangeRules = manga.scanlatorRangeRules,
        )
    } else {
        unblacklisted
    }
}

/**
 * Applies chapter-list display visibility without changing the visibility used by automatic
 * reading, downloads, History, or statistics. When blacklisted chapters are revealed, only
 * those explicit blacklist matches are added back; duplicate uploads remain merged normally.
 */
data class ChapterListVisibility(
    val chapters: List<Chapter>,
    val blacklistedChapterIds: Set<Long>,
)

fun List<Chapter>.chapterListVisibility(
    manga: Manga,
    smartScanlatorMergeEnabled: Boolean,
    hideBlacklistedChapters: Boolean,
    allChapters: List<Chapter> = this,
): ChapterListVisibility {
    val unblacklistedChapters = filterBlacklistedChapters(this, manga.blacklistedChapters, allChapters)
    val unblacklistedIds = unblacklistedChapters.mapTo(HashSet(), Chapter::id)
    val blacklistedIds = asSequence()
        .map(Chapter::id)
        .filterNot(unblacklistedIds::contains)
        .toSet()
    val visibleChapters = if (smartScanlatorMergeEnabled) {
        unblacklistedChapters.applySmartScanlatorMerge(
            priority = manga.scanlatorPriority,
            blacklistedChapters = emptyList(),
            rangeRules = manga.scanlatorRangeRules,
        )
    } else {
        unblacklistedChapters
    }

    return ChapterListVisibility(
        chapters = if (hideBlacklistedChapters) {
            visibleChapters
        } else {
            val visibleIds = visibleChapters.mapTo(HashSet(), Chapter::id)
            filter { it.id in visibleIds || it.id in blacklistedIds }
        },
        blacklistedChapterIds = blacklistedIds,
    )
}

/**
 * Resolves a chapter that Smart Scanlator Merge has hidden to the chosen duplicate. A hidden
 * chapter with no matching visible chapter stays unresolved so callers do not reopen it.
 */
fun List<Chapter>.resolveVisibleChapter(
    manga: Manga,
    smartScanlatorMergeEnabled: Boolean,
    chapterId: Long,
    visibleChapters: List<Chapter> = filterChapterVisibility(manga, smartScanlatorMergeEnabled),
): Chapter? {
    return visibleChapters.firstOrNull { it.id == chapterId }
        ?: firstOrNull { it.id == chapterId }
            ?.takeIf { it.chapterNumber.isFinite() && it.chapterNumber > 0.0 }
            ?.let { hidden -> visibleChapters.firstOrNull { it.chapterNumber == hidden.chapterNumber } }
}

data class VisibleChapterStats(
    val total: Long,
    val read: Long,
    val bookmarked: Long,
    val bookmarkedRead: Long,
)

fun List<Chapter>.visibleChapterStats() = VisibleChapterStats(
    total = size.toLong(),
    read = count(Chapter::read).toLong(),
    bookmarked = count(Chapter::bookmark).toLong(),
    bookmarkedRead = count { it.bookmark && it.read }.toLong(),
)

data class ScanlatorCoverage(
    val groups: List<String>,
    val hiddenCount: Int,
)

fun scanlatorCoverage(chapterNumbers: Collection<Double>, maxGroups: Int = 10): ScanlatorCoverage? {
    val scaledNumbers = chapterNumbers
        .filter { it.isFinite() && it > 0.0 }
        .map { Math.round(it * TENTHS) }
        .filter { it > 0L }
        .distinct()
        .sorted()
    if (scaledNumbers.isEmpty()) return null

    val ranges = buildList {
        var index = 0
        while (index < scaledNumbers.size) {
            var end = index
            while (end + 1 < scaledNumbers.size && scaledNumbers[end + 1] == scaledNumbers[end] + TENTHS) {
                end++
            }
            add(scaledNumbers[index] to scaledNumbers[end])
            index = end + 1
        }
    }
    val displayed = ranges.take(maxGroups)
    return ScanlatorCoverage(
        groups = displayed.map { (start, end) ->
            if (start == end) {
                start.asScanlatorCoverageLabel()
            } else {
                "${start.asScanlatorCoverageLabel()}–${end.asScanlatorCoverageLabel()}"
            }
        },
        hiddenCount = ranges.size - displayed.size,
    )
}

private fun Long.asScanlatorCoverageLabel(): String =
    if (this % TENTHS == 0L) (this / TENTHS).toString() else "${this / TENTHS}.${this % TENTHS}"
// KNS <--
