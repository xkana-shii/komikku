package eu.kanade.tachiyomi.data.library

// KNS -->
/**
 * Sources excluded from library updates, by name as the previous personal fork stored them. Like there,
 * the exclusion applies to every library update run (scheduled and started by hand); refreshing a
 * single entry is not a library update and is not affected.
 */
object LibraryUpdateSourceExclusion {

    /** The ids among [sourceIds] whose source name ([nameOf]) is excluded. */
    inline fun excludedIds(sourceIds: Collection<Long>, excludedNames: Set<String>, nameOf: (Long) -> String): Set<Long> {
        if (excludedNames.isEmpty()) return emptySet()
        return sourceIds.filterTo(mutableSetOf()) { nameOf(it) in excludedNames }
    }

    /**
     * The choices of the setting: the library's source names, sorted, plus names excluded earlier whose
     * source is not installed or has no entries now, so they stay visible and can be cleared.
     */
    fun entries(librarySourceNames: List<String>, excludedNames: Set<String>): Map<String, String> =
        (librarySourceNames + excludedNames).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).associateWith { it }
}
// KNS <--
