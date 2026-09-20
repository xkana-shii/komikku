package tachiyomi.domain.library.model

// KMK --> Field queries and exclusions over existing library and source metadata.
object LibrarySearchParser {
    private val aliases = mapOf(
        "a" to "artist", "c" to "character", "char" to "character", "cat" to "category",
        "f" to "female", "g" to "group", "creator" to "group", "circle" to "group",
        "l" to "language", "lang" to "language", "m" to "male", "p" to "parody",
        "series" to "parody", "r" to "reclass", "src" to "source", "tags" to "tag", "desc" to "description",
    )
    private val fields = setOf(
        "title", "author", "artist", "source", "genre", "tag", "status", "tracker", "description",
        "uploader", "id", "category", "character", "female", "group", "language", "male", "parody", "reclass",
    )

    fun parse(query: String): List<LibrarySearchToken> {
        val result = mutableListOf<LibrarySearchToken>()
        val text = StringBuilder()
        var quoted = false
        var wholeQuoted = false
        var excluded = false
        var exact = false
        var field: String? = null
        fun flush() {
            val value = text.toString()
            text.clear()
            if (!wholeQuoted && field == null && value == "NOT") {
                excluded = true
            } else if (value.isNotBlank() && (wholeQuoted || value !in setOf("AND", "OR"))) {
                result += LibrarySearchToken(value, field, excluded, exact)
                excluded = false
                exact = false
            }
            field = null
            wholeQuoted = false
        }
        for (char in query) {
            when {
                char == '"' -> {
                    if (!quoted && text.isEmpty() && field == null) wholeQuoted = true
                    quoted = !quoted
                }
                !quoted && (char.isWhitespace() || char == ',') -> flush()
                !quoted && text.isEmpty() && field == null && char == '-' -> excluded = true
                !quoted && text.isEmpty() && char == '$' -> exact = true
                !quoted && char == ':' && field == null && !wholeQuoted -> {
                    val prefix = text.toString().lowercase()
                    val mapped = aliases[prefix] ?: prefix
                    if (mapped in fields) {
                        field = mapped
                        text.clear()
                    } else {
                        text.append(char)
                    }
                }
                else -> text.append(char)
            }
        }
        flush()
        return result
    }
}
// KMK <--
