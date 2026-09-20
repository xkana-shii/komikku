package tachiyomi.domain.library.model

// KMK --> Transient query tokens; no library persistence changes.
data class LibrarySearchToken(
    val text: String,
    val field: String? = null,
    val excluded: Boolean = false,
    val exact: Boolean = false,
) {
    fun matches(values: Iterable<String>): Boolean = values.any {
        val numericField = field in setOf("id", "source", "status", "category") && text.toLongOrNull() != null
        if (exact || numericField) it.equals(text, ignoreCase = true) else it.contains(text, ignoreCase = true)
    }
}
// KMK <--
