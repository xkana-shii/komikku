package mihon.domain.library.model.search

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LibrarySearchParserTest {

    @Test
    fun `empty query parses to an empty and node`() {
        QueryNode.from("") shouldBe AndNode(emptyList())
    }

    @Test
    fun `plain terms become general nodes`() {
        QueryNode.from("reading") shouldBe GeneralQueryNode("reading", negated = false)
    }

    @Test
    fun `space separated terms are anded`() {
        QueryNode.from("foo bar") shouldBe AndNode(
            listOf(
                GeneralQueryNode("foo", negated = false),
                GeneralQueryNode("bar", negated = false),
            ),
        )
    }

    @Test
    fun `double pipe is an or`() {
        QueryNode.from("foo || bar") shouldBe OrNode(
            listOf(
                GeneralQueryNode("foo", negated = false),
                GeneralQueryNode("bar", negated = false),
            ),
        )
    }

    @Test
    fun `leading minus negates the term`() {
        QueryNode.from("-reading") shouldBe GeneralQueryNode("reading", negated = true)
    }

    @Test
    fun `known field is parsed as a field node`() {
        QueryNode.from("title:foo") shouldBe FieldQueryNode(MangaField.TITLE, "foo", negated = false)
    }

    @Test
    fun `unknown field falls back to a general node`() {
        QueryNode.from("unknown:foo") shouldBe GeneralQueryNode("unknown:foo", negated = false)
    }

    @Test
    fun `comparison field is parsed with comparator`() {
        QueryNode.from("unread>=5") shouldBe
            ComparisonQueryNode(ComparisonField.UNREAD, "5", Comparator.GTE, negated = false)
    }

    @Test
    fun `parenthesised or nested in an and with negation`() {
        val node = QueryNode.from("(foo || bar) -baz")
        node shouldBe AndNode(
            listOf(
                OrNode(
                    listOf(
                        GeneralQueryNode("foo", negated = false),
                        GeneralQueryNode("bar", negated = false),
                    ),
                ),
                // A negated bare term folds the negation into the general node ...
                GeneralQueryNode("baz", negated = true),
            ),
        )
    }

    @Test
    fun `negated parenthesised group becomes a not node`() {
        // ... whereas a negated group is wrapped in a NotNode
        QueryNode.from("-(foo || bar)") shouldBe NotNode(
            OrNode(
                listOf(
                    GeneralQueryNode("foo", negated = false),
                    GeneralQueryNode("bar", negated = false),
                ),
            ),
        )
    }

    @Test
    fun `field aliases resolve to the same field`() {
        QueryNode.from("lang:en") shouldBe FieldQueryNode(MangaField.LANGUAGE, "en", negated = false)
        QueryNode.from("tag:action") shouldBe FieldQueryNode(MangaField.GENRE, "action", negated = false)
    }

    @Test
    fun `quoted values keep separators`() {
        val node = QueryNode.from("title:\"foo bar\"")
        node.shouldBeInstanceOf<FieldQueryNode>()
        node.value shouldBe "foo bar"
    }

    // KUN --> TachiyomiSY compatibility shims

    @Test
    fun `comparison field without a comparator means equality`() {
        // SY users wrote `id:123`; before the shim it lexed as a Field and fell through to a literal term
        QueryNode.from("id:123") shouldBe
            ComparisonQueryNode(ComparisonField.ID, "123", Comparator.EQ, negated = false)
        QueryNode.from("id:123") shouldBe QueryNode.from("id=123")
    }

    @Test
    fun `numeric src alias matches the source id`() {
        // `src:123` used to match the source id; it must not silently match a source *name* containing "123"
        QueryNode.from("src:123") shouldBe FieldQueryNode(MangaField.SOURCE_ID, "123", negated = false)
        QueryNode.from("source:123") shouldBe FieldQueryNode(MangaField.SOURCE_ID, "123", negated = false)
    }

    @Test
    fun `non numeric src keeps matching the source name`() {
        QueryNode.from("src:foo") shouldBe FieldQueryNode(MangaField.SOURCE, "foo", negated = false)
        QueryNode.from("src:local") shouldBe FieldQueryNode(MangaField.SOURCE, "local", negated = false)
    }

    @Test
    fun `negated compatibility shims keep the negation`() {
        QueryNode.from("-src:123") shouldBe FieldQueryNode(MangaField.SOURCE_ID, "123", negated = true)
        QueryNode.from("-id:123") shouldBe
            ComparisonQueryNode(ComparisonField.ID, "123", Comparator.EQ, negated = true)
    }

    @Test
    fun `wildcard characters are preserved in general terms`() {
        // The actual wildcard matching lives in QueryNodeExtensions (app module); the parser only
        // has to leave `*`/`?` untouched instead of treating them as whitespace.
        QueryNode.from("foo*") shouldBe GeneralQueryNode("foo*", negated = false)
        QueryNode.from("f?o") shouldBe GeneralQueryNode("f?o", negated = false)
    }

    // KUN <--

    // KUN --> TachiyomiSY EH/NHentai metadata namespaces

    @Test
    fun `metadata long namespaces become metadata nodes`() {
        QueryNode.from("artist:foo") shouldBe MetadataQueryNode("artist", "foo", negated = false)
        QueryNode.from("group:foo") shouldBe MetadataQueryNode("group", "foo", negated = false)
        QueryNode.from("parody:foo") shouldBe MetadataQueryNode("parody", "foo", negated = false)
        QueryNode.from("character:foo") shouldBe MetadataQueryNode("character", "foo", negated = false)
        QueryNode.from("uploader:foo") shouldBe MetadataQueryNode("uploader", "foo", negated = false)
        QueryNode.from("female:foo") shouldBe MetadataQueryNode("female", "foo", negated = false)
        QueryNode.from("male:foo") shouldBe MetadataQueryNode("male", "foo", negated = false)
        QueryNode.from("reclass:foo") shouldBe MetadataQueryNode("reclass", "foo", negated = false)
    }

    @Test
    fun `metadata short aliases resolve to the canonical namespace`() {
        QueryNode.from("a:foo") shouldBe MetadataQueryNode("artist", "foo", negated = false)
        QueryNode.from("c:foo") shouldBe MetadataQueryNode("character", "foo", negated = false)
        QueryNode.from("char:foo") shouldBe MetadataQueryNode("character", "foo", negated = false)
        QueryNode.from("f:foo") shouldBe MetadataQueryNode("female", "foo", negated = false)
        QueryNode.from("g:foo") shouldBe MetadataQueryNode("group", "foo", negated = false)
        QueryNode.from("creator:foo") shouldBe MetadataQueryNode("group", "foo", negated = false)
        QueryNode.from("circle:foo") shouldBe MetadataQueryNode("group", "foo", negated = false)
        QueryNode.from("l:foo") shouldBe MetadataQueryNode("language", "foo", negated = false)
        QueryNode.from("m:foo") shouldBe MetadataQueryNode("male", "foo", negated = false)
        QueryNode.from("p:foo") shouldBe MetadataQueryNode("parody", "foo", negated = false)
        QueryNode.from("series:foo") shouldBe MetadataQueryNode("parody", "foo", negated = false)
        QueryNode.from("r:foo") shouldBe MetadataQueryNode("reclass", "foo", negated = false)
    }

    @Test
    fun `metadata namespaces are case insensitive`() {
        QueryNode.from("ARTIST:foo") shouldBe MetadataQueryNode("artist", "foo", negated = false)
        QueryNode.from("A:foo") shouldBe MetadataQueryNode("artist", "foo", negated = false)
    }

    @Test
    fun `metadata namespace keeps the negation`() {
        QueryNode.from("-artist:foo") shouldBe MetadataQueryNode("artist", "foo", negated = true)
    }

    @Test
    fun `metadata namespace honours quoted values`() {
        val node = QueryNode.from("artist:\"foo bar\"")
        node.shouldBeInstanceOf<MetadataQueryNode>()
        node.value shouldBe "foo bar"
    }

    @Test
    fun `language and lang stay mihon source language fields`() {
        // `language`/`lang` are used by the details-screen shortcut, so the SY `l` alias is the only
        // one that maps to the metadata language namespace.
        QueryNode.from("language:en") shouldBe FieldQueryNode(MangaField.LANGUAGE, "en", negated = false)
        QueryNode.from("lang:en") shouldBe FieldQueryNode(MangaField.LANGUAGE, "en", negated = false)
    }

    @Test
    fun `non metadata fields are unaffected by the metadata shim`() {
        QueryNode.from("title:foo") shouldBe FieldQueryNode(MangaField.TITLE, "foo", negated = false)
        QueryNode.from("tag:action") shouldBe FieldQueryNode(MangaField.GENRE, "action", negated = false)
        QueryNode.from("source:foo") shouldBe FieldQueryNode(MangaField.SOURCE, "foo", negated = false)
    }

    // KUN <--
}
