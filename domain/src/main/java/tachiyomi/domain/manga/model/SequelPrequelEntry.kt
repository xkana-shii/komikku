package tachiyomi.domain.manga.model

// KNS -->
/**
 * A title a tracker (or MangaDex) relates to a manga: sequel, prequel, side story, ... Relations are
 * transient: they are fetched for display and never create tracker bindings or library entries.
 */
data class SequelPrequelEntry(
    val title: String,
    val url: String,
    val relation: SequelPrequelRelation,
    val trackerId: Long? = null,
    val remoteId: Long? = null,
    val coverUrl: String? = null,
    /** The related title's URL in the manga's own source, when the relation comes from that source. */
    val sourceUrl: String? = null,
    /** The related title's local manga, when it could be identified. */
    val localMangaId: Long? = null,
    /** Whether [localMangaId] is in the library. */
    val inLibrary: Boolean = false,
)

enum class SequelPrequelRelation {
    PREQUEL,
    SEQUEL,
    ADAPTATION,
    ALTERNATIVE,
    SIDE_STORY,
    SPIN_OFF,
    PARENT,
    SUMMARY,
    CAMEO,
    CHARACTER_FOCUS,
    COMPILATION,
    CONTAINS,
    CROSSOVER,
    EXPANSION,
    MAIN,
    MAIN_STORY,
    PARODY,
    REBOOT,
    REMAKE,
    SAME_UNIVERSE,
    SERIES,
    SOURCE,
    UNCOLLECTED,
    DOUJINSHI,
    COLORED,
    MONOCHROME,
    ALTERNATE_STORY,
    ALTERNATE_VERSION,
    PRESERIALIZATION,
    SERIALIZATION,
    OTHER,
    ;

    companion object {
        /** Maps a service's relation name (`SIDE_STORY`, `side_story`, `side-story`, ...) to a relation. */
        fun from(value: String): SequelPrequelRelation {
            val normalized = value.trim().replace('-', '_').replace(' ', '_')
            return entries.find { it.name.equals(normalized, ignoreCase = true) } ?: OTHER
        }
    }
}
// KNS <--
