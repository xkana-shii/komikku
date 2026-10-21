package tachiyomi.domain.category.model

// KNS -->
/**
 * Hierarchy information that accompanies categories restored from a backup or sync payload, keyed
 * by the categories' (effective) sync uids.
 */
data class RestoredCategoryHierarchy(
    /** Parent uid per category uid; 0 = root. A missing key means the parent is unknown. */
    val parentUids: Map<Long, Long> = emptyMap(),
    /** Uids whose payload carries the fork fields (Smart Category status, update interval). */
    val carriesForkFields: Set<Long> = emptySet(),
)
// KNS <--
