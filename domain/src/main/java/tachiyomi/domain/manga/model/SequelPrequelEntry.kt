package tachiyomi.domain.manga.model

// KMK --> Relations are transient. Opening one never inserts a tracker stub.
data class SequelPrequelEntry(
    val title: String,
    val url: String,
    val relation: SequelPrequelRelation,
    val trackerId: Long? = null,
    val remoteId: Long? = null,
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
    val localMangaId: Long? = null,
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
        fun from(value: String): SequelPrequelRelation = entries.find { it.name.equals(value, true) } ?: OTHER
    }
}
// KMK <--
