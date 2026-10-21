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
    }
}
// KNS <--
