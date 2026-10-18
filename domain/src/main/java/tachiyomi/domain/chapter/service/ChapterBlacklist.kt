package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.roundToInt

// KMK -->
private const val MAX_KEY_SCANLATOR_LENGTH = 64
private const val MAX_KEY_NAME_LENGTH = 256
private const val URL_KEY_PREFIX = "url:"
private const val HYBRID_KEY_PREFIX = "hybrid:"
private const val HYBRID_KEY_V2_PREFIX = "${HYBRID_KEY_PREFIX}v2:"

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

private fun normalizedHybridName(name: String): String = name
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .take(MAX_KEY_NAME_LENGTH)

/**
 * A versioned key carrying enough stable metadata to recover a chapter after a URL change.
 *
 * The original hybrid key had only a number and scanlator fallback. Some sources, including
 * Mangago, give announcements the same number and scanlator as a real chapter. Version two adds
 * the source chapter name so a refreshed announcement remains distinguishable without allowing a
 * broad number-and-scanlator match to hide its real chapter.
 */
fun hybridChapterBlacklistKey(chapter: Chapter): String {
    val number = normalizedHybridNumber(chapter.chapterNumber).orEmpty()
    return HYBRID_KEY_V2_PREFIX + listOf(
        number,
        normalizedHybridScanlator(chapter.scanlator),
        chapter.url,
        normalizedHybridName(chapter.name),
    ).joinToString("|", transform = ::encodeBlacklistComponent)
}

private data class HybridBlacklistEntry(
    val number: String?,
    val scanlator: String,
    val url: String,
    val name: String?,
)

private fun parseHybridBlacklistKey(key: String): HybridBlacklistEntry? {
    return when {
        key.startsWith(HYBRID_KEY_V2_PREFIX) -> {
            val values = key.removePrefix(HYBRID_KEY_V2_PREFIX).split('|', limit = 4)
            if (values.size != 4) return null
            HybridBlacklistEntry(
                number = values[0].takeIf(String::isNotEmpty),
                scanlator = decodeBlacklistComponent(values[1]),
                url = decodeBlacklistComponent(values[2]),
                name = decodeBlacklistComponent(values[3]).takeIf(String::isNotEmpty),
            )
        }
        key.startsWith(HYBRID_KEY_PREFIX) -> {
            val values = key.removePrefix(HYBRID_KEY_PREFIX).split('|', limit = 3)
            if (values.size != 3) return null
            HybridBlacklistEntry(
                number = values[0].takeIf(String::isNotEmpty),
                scanlator = decodeBlacklistComponent(values[1]),
                url = decodeBlacklistComponent(values[2]),
                name = null,
            )
        }
        else -> null
    }
}

private fun chapterUrlKeySet(chapters: Iterable<Chapter>): Set<String> =
    chapters.mapTo(HashSet(), Chapter::url).mapTo(HashSet(), ::chapterUrlBlacklistKey)

/**
 * Resolves one stored entry against a complete chapter snapshot.
 *
 * Hybrid entries prefer their exact URL. If it has disappeared, the version-two name discriminator
 * is considered before the old number-and-scanlator fallback. Every fallback must identify exactly
 * one chapter in [chapters]; ambiguous legacy or refresh matches are deliberately left visible.
 */
fun resolveChapterBlacklistKey(key: String, chapters: List<Chapter>): Set<String> {
    val byUrl = chapters.associateBy { chapterUrlBlacklistKey(it.url) }
    if (key.startsWith(URL_KEY_PREFIX)) return setOf(key).intersect(byUrl.keys)

    val hybrid = parseHybridBlacklistKey(key)
    if (hybrid != null) {
        val exact = chapterUrlBlacklistKey(hybrid.url)
        if (exact in byUrl) return setOf(exact)
        val metadataMatches = chapters.filter {
            (hybrid.number == null || normalizedHybridNumber(it.chapterNumber) == hybrid.number) &&
                normalizedHybridScanlator(it.scanlator) == hybrid.scanlator
        }
        val nameMatches = hybrid.name?.let { name ->
            metadataMatches.filter { normalizedHybridName(it.name) == name }
        }
        if (nameMatches != null) {
            return nameMatches.takeIf { it.size == 1 }?.let(::chapterUrlKeySet).orEmpty()
        }
        if (hybrid.number == null) return emptySet()
        return metadataMatches.takeIf { it.size == 1 }?.let(::chapterUrlKeySet).orEmpty()
    }

    // Legacy number-and-scanlator entries have no URL. Reconcile them only when the current
    // snapshot has exactly one candidate; guessing would hide unrelated extras and duplicates.
    val matches = chapters.filter {
        scanlatorBlacklistKey(it.chapterNumber, it.scanlator) == key
    }
    return matches.takeIf { it.size == 1 }?.let(::chapterUrlKeySet).orEmpty()
}

/**
 * Filters [chapters] using [allChapters] as the authoritative snapshot for fallback resolution.
 *
 * A visible subset can make an old broad key look unique even when it collides in the manga's full
 * chapter list. Callers that work with a subset must therefore pass the complete snapshot.
 */
fun filterBlacklistedChapters(
    chapters: List<Chapter>,
    blacklist: Collection<String>,
    allChapters: List<Chapter>? = null,
): List<Chapter> {
    if (chapters.isEmpty() || blacklist.isEmpty()) return chapters
    val blacklistedUrls = blacklist.mapNotNullTo(HashSet()) { key ->
        when {
            key.startsWith(URL_KEY_PREFIX) -> key
            else -> parseHybridBlacklistKey(key)?.let { chapterUrlBlacklistKey(it.url) }
        }
    }
    allChapters?.let { snapshot ->
        blacklist.flatMapTo(blacklistedUrls) { resolveChapterBlacklistKey(it, snapshot) }
    }
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

/**
 * Single-chapter checks intentionally only honour exact URL-bearing entries. Legacy keys need a
 * complete chapter snapshot to establish whether their number-and-scanlator fallback is unique;
 * callers that filter a list use [filterBlacklistedChapters] for that resolution.
 */
fun isChapterBlacklisted(chapter: Chapter, blacklist: Collection<String>): Boolean {
    return blacklist.isNotEmpty() && (
        chapterUrlBlacklistKey(chapter.url) in blacklist ||
            blacklist.any { key ->
                parseHybridBlacklistKey(key)?.let { chapterUrlBlacklistKey(it.url) == chapterUrlBlacklistKey(chapter.url) } == true
            }
        )
}
// KMK <--
