package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import kotlin.math.roundToInt

// KMK -->
private const val MAX_KEY_SCANLATOR_LENGTH = 64
private const val URL_KEY_PREFIX = "url:"

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

/** Converts a legacy Fillermark to an exact chapter key. */
fun legacyFillermarkBlacklistKeys(urls: Iterable<String>): List<String> {
    return urls.map(::chapterUrlBlacklistKey).distinct()
}

/** The single matcher used by every surface that displays or acts on chapters. */
fun isChapterBlacklisted(chapter: Chapter, blacklist: Collection<String>): Boolean {
    return blacklist.isNotEmpty() && (
        chapterUrlBlacklistKey(chapter.url) in blacklist ||
            scanlatorBlacklistKey(chapter.chapterNumber, chapter.scanlator) in blacklist
        )
}
// KMK <--
