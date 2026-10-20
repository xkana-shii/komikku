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
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L

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
    }
}
