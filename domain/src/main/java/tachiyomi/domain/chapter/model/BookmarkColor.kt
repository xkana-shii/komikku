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
         * Whether a manga or chapter with bookmarks of [colors] (stored values) passes [filter]. An
         * empty filter passes everything.
         */
        fun matchesFilter(colors: Collection<Int>, filter: Set<BookmarkColor>): Boolean =
            filter.isEmpty() || colors.any { fromValue(it) in filter }
    }
}
// KNS <--
