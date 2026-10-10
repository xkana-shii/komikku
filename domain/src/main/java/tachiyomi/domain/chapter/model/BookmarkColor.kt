package tachiyomi.domain.chapter.model

// KMK -->
/** A local, optional annotation for a bookmarked chapter. */
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
        fun fromValue(value: Long): BookmarkColor {
            return entries.firstOrNull { it.value == value.toInt() } ?: NONE
        }
    }
}
// KMK <--
