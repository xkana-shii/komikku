package tachiyomi.domain.manga.model

// KMK --> Only explicit media classifications, never title or description guesses.
fun isNovelFormat(format: String?): Boolean = when (format?.filterNot { it.isWhitespace() || it == '_' || it == '-' }?.uppercase()) {
    "NOVEL", "LIGHTNOVEL", "WEBNOVEL" -> true
    else -> false
}
// KMK <--
