package eu.kanade.tachiyomi.source.online.all

// KNS -->
/**
 * The previous personal fork's "Clean title" for E-Hentai/ExHentai: removes [bracketed], (parenthesised)
 * and {braced} tags such as the circle, event and language from a gallery title. Only the titles the
 * E-Hentai source fetches go through it; custom titles and other sources are untouched.
 */
object EhCleanTitle {
    private val tags = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

    fun apply(title: String, enabled: Boolean): String = if (enabled) title.replace(tags, "").trim() else title
}
// KNS <--
