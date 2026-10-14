package mihon.domain.library.model.search

class LibrarySearchParser(private val tokens: List<LibrarySearchLexer.Token>) {
    private var index = 0

    private fun peek(): LibrarySearchLexer.Token? = tokens.getOrNull(index)
    private fun advance(): LibrarySearchLexer.Token? = tokens.getOrNull(index++)

    fun parse(): QueryNode {
        if (tokens.isEmpty()) return AndNode(emptyList())
        return parseOr()
    }

    private fun parseOr(): QueryNode {
        val nodes = mutableListOf<QueryNode>()
        nodes.add(parseAnd())
        while (peek() is LibrarySearchLexer.Token.Or) {
            advance()
            nodes.add(parseAnd())
        }
        return if (nodes.size == 1) nodes.first() else OrNode(nodes)
    }

    private fun parseAnd(): QueryNode {
        val nodes = mutableListOf<QueryNode>()

        while (index < tokens.size && peek() !is LibrarySearchLexer.Token.Or &&
            peek() !is LibrarySearchLexer.Token.RParen
        ) {
            if (peek() is LibrarySearchLexer.Token.And) {
                advance()
            }
            nodes.add(parseTerm())
        }

        val filteredNodes = nodes.filter { it !is EmptyQueryNode }
        if (filteredNodes.isEmpty()) return EmptyQueryNode
        return if (filteredNodes.size == 1) filteredNodes.first() else AndNode(filteredNodes)
    }

    private fun parseTerm(): QueryNode {
        var negated = false

        while (peek() is LibrarySearchLexer.Token.Not) {
            advance()
            negated = !negated
        }

        if (peek() is LibrarySearchLexer.Token.LParen) {
            advance()

            val subTree = parseOr()

            if (peek() is LibrarySearchLexer.Token.RParen) {
                advance()
            }

            if (negated) {
                return NotNode(subTree)
            }
            return subTree
        }

        return when (val nextToken = advance()) {
            is LibrarySearchLexer.Token.General -> GeneralQueryNode(nextToken.value, negated)
            is LibrarySearchLexer.Token.Field -> {
                // KUN -->
                // TachiyomiSY EH/NHentai metadata namespaces (`artist:`, `g:`, ...) take precedence
                // over Mihon's same-named fields, because the metadata meaning is the useful one for
                // EH/NHentai libraries (`artist:` used to match the manga's group/artist field).
                val metadataNamespace = MetadataNamespace.canonical(nextToken.field)
                // TachiyomiSY compatibility shims (Mihon's grammar otherwise treats these as literals):
                //  - `src:<digits>` used to match the *source id*, not the source name
                //  - `id:<n>` (and any comparator-less comparison field) means equality, i.e. `=`
                val field = MangaField.fromString(nextToken.field)
                val numericSource = field == MangaField.SOURCE &&
                    nextToken.value.isNotEmpty() &&
                    nextToken.value.all { it.isDigit() }
                val comparisonField = if (field == null) {
                    ComparisonField.fromString(nextToken.field)
                } else {
                    null
                }
                when {
                    metadataNamespace != null ->
                        MetadataQueryNode(metadataNamespace, nextToken.value, negated)
                    numericSource -> FieldQueryNode(MangaField.SOURCE_ID, nextToken.value, negated)
                    field != null -> FieldQueryNode(field, nextToken.value, negated)
                    comparisonField != null ->
                        ComparisonQueryNode(comparisonField, nextToken.value, Comparator.EQ, negated)
                    else -> GeneralQueryNode("${nextToken.field}:${nextToken.value}", negated)
                }
                // KUN <--
            }
            is LibrarySearchLexer.Token.CompField -> {
                ComparisonField.fromString(nextToken.field)?.let {
                    ComparisonQueryNode(it, nextToken.value, Comparator.fromString(nextToken.comparator)!!, negated)
                } ?: GeneralQueryNode("${nextToken.field}${nextToken.comparator}${nextToken.value}", negated)
            }
            else -> EmptyQueryNode
        }
    }
}
