package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    // KMK -->
    val hidden: Boolean,
    // KMK <--
    // SY -->
    val version: Long = 0,
    val uid: Long = 0,
    val lastModifiedAt: Long = 0,
    // SY <--
    // KNS -->
    /** Parent category id; [UNCATEGORIZED_ID] (0) for a root category. */
    val parentId: Long = UNCATEGORIZED_ID,
    /** Tracker status bound to this category (Smart Category); null for an ordinary category. */
    val trackStatus: Int? = null,
    /** Library update interval in days; null to follow the global schedule. */
    val updateInterval: Int? = null,
    /** Creation/modification time (epoch millis) for the category manager's date sort; 0 = unknown. */
    val dateAdded: Long = DATE_UNKNOWN,
    val dateModified: Long = DATE_UNKNOWN,
    // KNS <--
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L

        // KNS -->
        const val DATE_UNKNOWN = 0L
        // KNS <--

        // KUN -->
        /**
         * Computes a deterministic positive 63-bit UID from a category name.
         * Used to ensure that categories sharing the same name across multiple devices converge to
         * the exact same identity, preventing random UID collisions or divergence during sync.
         */
        fun nameToUid(name: String): Long {
            val normalized = name.trim().lowercase()
            if (normalized.isEmpty()) return 0L
            var h = 1125899906842597L
            for (ch in normalized) {
                h = 31L * h + ch.code
            }
            val positive = h and Long.MAX_VALUE
            return if (positive == 0L) 1L else positive
        }
        // KUN <--

        // KNS -->
        /**
         * Deterministic UID for a category below [parentUid]. Roots keep [nameToUid] so Komi-kun's
         * cross-device convergence is unchanged, while `Shounen / Reading` and `Josei / Reading`
         * get different identities instead of colliding on the shared name.
         */
        fun childUid(parentUid: Long, name: String): Long {
            if (parentUid == 0L) return nameToUid(name)
            val normalized = name.trim().lowercase()
            if (normalized.isEmpty()) return 0L
            var h = parentUid xor 0x2545F4914F6CDD1DL
            for (ch in normalized) {
                h = 31L * h + ch.code
            }
            val positive = h and Long.MAX_VALUE
            return if (positive == 0L) 1L else positive
        }
        // KNS <--
    }
}
