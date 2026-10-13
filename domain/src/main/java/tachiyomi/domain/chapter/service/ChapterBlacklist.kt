package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.roundToInt

// KMK -->
private const val MAX_KEY_SCANLATOR_LENGTH = 64
private const val URL_KEY_PREFIX = "url:"
private const val HYBRID_KEY_PREFIX = "hybrid:"

/** Returns Houri's per-number-and-scanlator blacklist key. */
fun scanlatorBlacklistKey(chapterNumber: Double, scanlator: String?): String {
    val rounded = (chapterNumber * 100).roundToInt() / 100.0
    val normalizedScanlator = scanlator.orEmpty().trim().take(MAX_KEY_SCANLATOR_LENGTH)
    return "$rounded@$normalizedScanlator"
}

/**
 * A collision-free key for migrated legacy Fillermarks and old backup fields.
 *
 * The list column separates items with `, `, so commas and percent characters are escaped. The
 * same canonicalization is used for lookup, preserving the source URL without relying on a local
 * database id.
 */
fun chapterUrlBlacklistKey(url: String): String {
    return URL_KEY_PREFIX + url.replace("%", "%25").replace(",", "%2C")
}

// KMK -->
private fun encodeBlacklistComponent(value: String): String = value
    .replace("%", "%25")
    .replace(",", "%2C")
    .replace("|", "%7C")

private fun decodeBlacklistComponent(value: String): String = value
    .replace("%7C", "|")
    .replace("%2C", ",")
    .replace("%25", "%")

private fun normalizedHybridNumber(chapterNumber: Double): String? {
    if (!chapterNumber.isFinite() || chapterNumber <= 0.0) return null
    val rounded = (chapterNumber * 100).roundToInt() / 100.0
    return rounded.toString()
}

private fun normalizedHybridScanlator(scanlator: String?): String = scanlator.orEmpty()
    .trim()
    .lowercase()
    .take(MAX_KEY_SCANLATOR_LENGTH)

/** A versioned key carrying all stable metadata needed to recover a chapter after a URL change. */
fun hybridChapterBlacklistKey(chapter: Chapter): String {
    val number = normalizedHybridNumber(chapter.chapterNumber).orEmpty()
    return HYBRID_KEY_PREFIX + listOf(
        number,
        normalizedHybridScanlator(chapter.scanlator),
        chapter.url,
    ).joinToString("|", transform = ::encodeBlacklistComponent)
}

private data class HybridBlacklistEntry(
    val number: String?,
    val scanlator: String,
    val url: String,
)

private fun parseHybridBlacklistKey(key: String): HybridBlacklistEntry? {
    if (!key.startsWith(HYBRID_KEY_PREFIX)) return null
    val values = key.removePrefix(HYBRID_KEY_PREFIX).split('|', limit = 3)
    if (values.size != 3) return null
    return HybridBlacklistEntry(
        number = values[0].takeIf(String::isNotEmpty),
        scanlator = decodeBlacklistComponent(values[1]),
        url = decodeBlacklistComponent(values[2]),
    )
}

private fun chapterUrlKeySet(chapters: Iterable<Chapter>): Set<String> =
    chapters.mapTo(HashSet(), Chapter::url).mapTo(HashSet(), ::chapterUrlBlacklistKey)

/**
 * Resolves one stored entry against a complete chapter snapshot.
 *
 * Hybrid entries prefer their exact URL. If it has disappeared, metadata is used only when it
 * identifies one chapter; ambiguous replacements are deliberately left visible.
 */
fun resolveChapterBlacklistKey(key: String, chapters: List<Chapter>): Set<String> {
    val byUrl = chapters.associateBy { chapterUrlBlacklistKey(it.url) }
    if (key.startsWith(URL_KEY_PREFIX)) return setOf(key).intersect(byUrl.keys)

    val hybrid = parseHybridBlacklistKey(key)
    if (hybrid != null) {
        val exact = chapterUrlBlacklistKey(hybrid.url)
        if (exact in byUrl) return setOf(exact)
        if (hybrid.number == null) return emptySet()
        val matches = chapters.filter {
            normalizedHybridNumber(it.chapterNumber) == hybrid.number &&
                normalizedHybridScanlator(it.scanlator) == hybrid.scanlator
        }
        return matches.takeIf { it.size == 1 }?.let(::chapterUrlKeySet).orEmpty()
    }

    // Legacy number-and-scanlator entries intentionally retain their historical all-matches
    // semantics because they contain no URL with which to disambiguate old data.
    val matches = chapters.filter {
        scanlatorBlacklistKey(it.chapterNumber, it.scanlator) == key
    }
    return chapterUrlKeySet(matches)
}

fun filterBlacklistedChapters(chapters: List<Chapter>, blacklist: Collection<String>): List<Chapter> {
    if (chapters.isEmpty() || blacklist.isEmpty()) return chapters
    val blacklistedUrls = blacklist.flatMapTo(HashSet()) { resolveChapterBlacklistKey(it, chapters) }
    return chapters.filterNot { chapterUrlBlacklistKey(it.url) in blacklistedUrls }
}

fun resolveChapterBlacklistMatches(key: String, chapters: List<Chapter>): List<Chapter> {
    val urls = resolveChapterBlacklistKey(key, chapters)
    return chapters.filter { chapterUrlBlacklistKey(it.url) in urls }
}

fun chapterBlacklistKeyMetadata(key: String): Pair<Double?, String?> {
    parseHybridBlacklistKey(key)?.let { hybrid ->
        return hybrid.number?.toDoubleOrNull() to hybrid.scanlator.takeIf(String::isNotEmpty)
    }
    val number = key.substringBefore('@').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val scanlator = key.substringAfter('@', "").trim().takeIf(String::isNotEmpty)
    return number to scanlator
}
// KMK <--

/** Converts a legacy Fillermark to an exact chapter key. */
fun legacyFillermarkBlacklistKeys(urls: Iterable<String>): List<String> {
    return urls.map(::chapterUrlBlacklistKey).distinct()
}

/** The single matcher used by every surface that displays or acts on chapters. */
fun isChapterBlacklisted(chapter: Chapter, blacklist: Collection<String>): Boolean {
    return blacklist.isNotEmpty() && (
        chapterUrlBlacklistKey(chapter.url) in blacklist ||
            blacklist.any { key ->
                parseHybridBlacklistKey(key)?.let { chapterUrlBlacklistKey(it.url) == chapterUrlBlacklistKey(chapter.url) } == true
            } ||
            scanlatorBlacklistKey(chapter.chapterNumber, chapter.scanlator) in blacklist
        )
}
// KMK <--
