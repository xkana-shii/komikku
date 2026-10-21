package mihon.domain.library.model.search

enum class MangaField(vararg val aliases: String, val fieldOnly: Boolean = false) {
    TITLE("title"),
    AUTHOR("author"),
    ARTIST("artist"),
    DESCRIPTION("description", "desc"),
    GENRE("genre", "tag"),
    SOURCE("source", "src"),
    NOTES("notes", "note"),
    LANGUAGE("language", "lang", fieldOnly = true),
    SOURCE_ID("source_id", "sourceid", "src_id", "srcid", fieldOnly = true),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): MangaField? = lookup[value.lowercase()]
    }
}

enum class ComparisonField(vararg val aliases: String) {
    ID("id"),
    DATE_ADDED("added"),
    FETCH_INTERVAL("fetchinterval", "fi"),
    NEXT_UPDATE("nextupdate", "nu"),
    UNREAD("unread"),
    READ("read"),
    TOTAL("total"),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): ComparisonField? = lookup[value.lowercase()]
    }
}

enum class Comparator(val symbol: String) {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<"),
    EQ("="),
    ;

    fun <T : Comparable<T>> apply(a: T, b: T): Boolean = when (this) {
        GTE -> a >= b
        LTE -> a <= b
        GT -> a > b
        LT -> a < b
        EQ -> a == b
    }

    companion object {
        private val lookup = entries.associateBy { it.symbol }

        fun fromString(value: String): Comparator? = lookup[value]
    }
}

sealed interface QueryNode {
    companion object {
        fun from(query: String): QueryNode {
            val tokens = LibrarySearchLexer.tokenize(query)
            return LibrarySearchParser(tokens).parse()
        }
    }
}

data class AndNode(val children: List<QueryNode>) : QueryNode

data class OrNode(val children: List<QueryNode>) : QueryNode

data class NotNode(val child: QueryNode) : QueryNode

object EmptyQueryNode : QueryNode

data class GeneralQueryNode(val value: String, val negated: Boolean) : QueryNode

data class FieldQueryNode(val field: MangaField, val value: String, val negated: Boolean) : QueryNode

data class ComparisonQueryNode(
    val field: ComparisonField,
    val value: String,
    val queryComparator: Comparator,
    val negated: Boolean,
) : QueryNode

// KUN -->
/**
 * A TachiyomiSY EH/NHentai metadata namespace term (e.g. `artist:foo`, `g:circle`).
 *
 * Mihon's AST has no equivalent field, so the parser emits this node and the app layer resolves it
 * against the stored flat metadata (`search_tags`, plus the uploader column) instead of silently
 * binding it to an unrelated manga field.
 */
data class MetadataQueryNode(
    val namespace: String,
    val value: String,
    val negated: Boolean,
) : QueryNode

/**
 * TachiyomiSY EH/NHentai tag namespaces and their short/long aliases (see the old
 * `exh.search.SearchEngine.parseQuery` alias table).
 *
 * `language`/`lang` are deliberately **not** listed: Mihon's AST already binds them to the source
 * language (and the details-screen shortcut relies on that), so only the short `l` alias keeps the
 * TachiyomiSY metadata meaning. `genre`/`tag`, `source`/`src`, `title`, `author`, ... likewise stay
 * Mihon fields.
 */
object MetadataNamespace {
    private val aliases = mapOf(
        "a" to "artist",
        "artist" to "artist",
        "c" to "character",
        "char" to "character",
        "character" to "character",
        "f" to "female",
        "female" to "female",
        "g" to "group",
        "creator" to "group",
        "circle" to "group",
        "group" to "group",
        "l" to "language",
        "m" to "male",
        "male" to "male",
        "p" to "parody",
        "series" to "parody",
        "parody" to "parody",
        "r" to "reclass",
        "reclass" to "reclass",
        "uploader" to "uploader",
    )

    /** Returns the canonical namespace for [value], or null when it is not a metadata namespace. */
    fun canonical(value: String): String? = aliases[value.lowercase()]
}
// KUN <--

// KNS -->
/**
 * The fork's library search fields that are resolved against data outside the manga row: the
 * entry's categories (with their parents), its trackers and its publishing status. Same names and
 * aliases as the previous personal fork's parser.
 */
enum class LibraryField(vararg val aliases: String) {
    CATEGORY("category", "cat"),
    TRACKER("tracker"),
    STATUS("status"),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): LibraryField? = lookup[value.lowercase()]
    }
}

/** A [LibraryField] term; the library supplies the values it is matched against. */
data class LibraryFieldQueryNode(
    val field: LibraryField,
    val value: String,
    val negated: Boolean,
) : QueryNode {
    /**
     * A number matches one of [ids] exactly (category id, status code); otherwise the term matches a
     * [names] entry that contains it, ignoring case.
     */
    fun matches(ids: Collection<Long>, names: Collection<String>): Boolean {
        val id = value.toLongOrNull()
        val match = (id != null && id in ids) || names.any { it.contains(value, ignoreCase = true) }
        return if (negated) !match else match
    }
}
// KNS <--
