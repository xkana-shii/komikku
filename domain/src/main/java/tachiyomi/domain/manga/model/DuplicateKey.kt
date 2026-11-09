package tachiyomi.domain.manga.model

// KNS -->
/**
 * The identifying fields of a library entry that duplicate detection compares: every title it is known under
 * (custom and source) and every author name.
 */
data class DuplicateKey(
    val id: Long,
    val titles: List<String>,
    val authors: List<String>,
)
// KNS <--
