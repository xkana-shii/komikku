package eu.kanade.tachiyomi.util.chapter

// KMK -->
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.isChapterBlacklisted

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
    val visible = filterNot { isChapterBlacklisted(it, blacklistedChapters) }
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
// KMK <--
