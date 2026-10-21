package tachiyomi.domain.chapter.service

// KNS -->
private const val URL_KEY_PREFIX = "url:"

/**
 * The exact, collision-free blacklist key for the chapter at [url].
 *
 * `manga.user_blacklisted_chapters` is a `, `-separated list, so commas and percent characters are
 * escaped. The format is the previous personal fork's, so its databases, migrated Fillermarks and
 * backups resolve to the same chapters.
 */
fun chapterUrlBlacklistKey(url: String): String {
    return URL_KEY_PREFIX + url.replace("%", "%25").replace(",", "%2C")
}

/** Converts legacy Fillermarks (identified by chapter URL) to exact blacklist keys. */
fun legacyFillermarkBlacklistKeys(urls: Iterable<String>): List<String> {
    return urls.map(::chapterUrlBlacklistKey).distinct()
}
// KNS <--
