package tachiyomi.domain.chapter.model

// KNS -->
/**
 * The fixed bookmark palette. [value] is what `chapter.user_bookmark_color` and backups store; the
 * numbers match the previous personal fork so its databases and backups keep their colors.
 * [NONE] is an ordinary bookmark color.
 */
enum class BookmarkColor(val value: Int) {
    NONE(0),
    RED(1),
    ORANGE(2),
    YELLOW(3),
    GREEN(4),
    BLUE(5),
    PURPLE(6),
    ;

    companion object {
        const val DEFAULT_ID = 0

        fun fromValue(value: Int): BookmarkColor = entries.firstOrNull { it.value == value } ?: NONE

        /** Colors stored by name in a filter preference; unknown names are ignored. */
        fun fromNames(names: Collection<String>): Set<BookmarkColor> =
            entries.filterTo(mutableSetOf()) { it.name in names }

        /**
         * Whether bookmarked colors pass the shared include/exclude filter. Included colors require
         * at least one match; exclusions always reject their matching color. This lets a manga with
         * several bookmark colors remain predictable: it passes when it has an included color and
         * none of its bookmarked colors are excluded.
         */
        fun matchesFilter(
            colors: Collection<Int>,
            included: Set<BookmarkColor>,
            excluded: Set<BookmarkColor> = emptySet(),
        ): Boolean {
            val palette = colors.map(::fromValue).toSet()
            return palette.none(excluded::contains) && (included.isEmpty() || palette.any(included::contains))
        }
    }
}
// KNS <--
