package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import exh.metadata.sql.models.SearchTag
import io.kotest.matchers.shouldBe
import mihon.domain.library.model.search.QueryNode
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class QueryNodeExtensionsTest {

    private fun item(
        id: Long = 1L,
        sourceId: Long = 1L,
        sourceName: String = "MangaDex",
        title: String = "Title",
    ): LibraryItem {
        val manga = Manga.create().copy(id = id, source = sourceId, ogTitle = title)
        return LibraryItem(
            libraryManga = LibraryManga(
                manga = manga,
                categories = listOf(0L),
                totalChapters = 0L,
                readCount = 0L,
                bookmarkCount = 0L,
                bookmarkReadCount = 0L,
                chapterFlags = 0L,
                latestUpload = null,
                chapterFetchedAt = null,
                lastRead = null,
            ),
            sourceName = sourceName,
            sourceLanguage = "en",
            badges = LibraryItem.Badges(
                downloadCount = 0,
                unreadCount = 0L,
                isLocal = false,
                sourceLanguage = "en",
            ),
        )
    }

    private fun matches(query: String, item: LibraryItem): Boolean = QueryNode.from(query).matches(item)

    @Test
    fun `id colon is id equality`() {
        val target = item(id = 123L)
        matches("id:123", target) shouldBe true
        matches("id=123", target) shouldBe true
        matches("id:124", target) shouldBe false
        matches("-id:123", target) shouldBe false
    }

    @Test
    fun `numeric src matches the source id`() {
        val target = item(id = 5L, sourceId = 123L, sourceName = "MangaDex")
        matches("src:123", target) shouldBe true
        matches("srcid:123", target) shouldBe true
        matches("src:124", target) shouldBe false
        // It must not match a source *name* that happens to contain the digits
        matches("src:123", item(id = 5L, sourceId = 7L, sourceName = "Source 123")) shouldBe false
    }

    @Test
    fun `non numeric src matches the source name`() {
        val target = item(sourceId = 7L, sourceName = "MangaDex")
        matches("src:dex", target) shouldBe true
        // A numeric term is resolved as a source id, so it does not match this source
        matches("src:123", target) shouldBe false
    }

    @Test
    fun `src local matches the local source`() {
        // LocalSource.ID is 0; the name deliberately does not contain "local"
        matches("src:local", item(sourceId = 0L, sourceName = "MangaDex")) shouldBe true
        matches("src:local", item(sourceId = 123L, sourceName = "MangaDex")) shouldBe false
    }

    @Test
    fun `wildcards match substrings`() {
        val target = item(title = "Foobar")
        matches("foo*", target) shouldBe true
        matches("foo%", target) shouldBe true
        matches("f?o", target) shouldBe true
        matches("*bar", target) shouldBe true
        matches("f*z", target) shouldBe false
    }

    @Test
    fun `plain terms stay substring matches`() {
        matches("oob", item(title = "Foobar")) shouldBe true
        matches("nope", item(title = "Foobar")) shouldBe false
    }

    // KUN --> TachiyomiSY EH/NHentai metadata namespace search

    private fun tag(namespace: String?, name: String): SearchTag =
        SearchTag(id = null, mangaId = 1L, namespace = namespace, name = name, type = 0)

    private fun metadata(
        uploader: String? = null,
        tags: List<SearchTag> = emptyList(),
    ): LibrarySearchMetadata = LibrarySearchMetadata(uploader = uploader, tags = tags)

    private fun matches(query: String, item: LibraryItem, metadata: LibrarySearchMetadata?): Boolean =
        QueryNode.from(query).matches(item, metadata)

    @Test
    fun `metadata namespace matches the stored tag`() {
        val target = item()
        val meta = metadata(
            tags = listOf(
                tag("artist", "Foo Bar"),
                tag("group", "Circle X"),
                tag("parody", "Series Y"),
                tag("character", "Char Z"),
                tag("female", "Futa"),
                tag("male", "Mob"),
                tag("reclass", "Rewritten"),
                tag("language", "English"),
            ),
        )

        matches("artist:foo", target, meta) shouldBe true
        matches("artist:bar", target, meta) shouldBe true
        matches("artist:nope", target, meta) shouldBe false

        matches("a:foo", target, meta) shouldBe true
        matches("group:circle", target, meta) shouldBe true
        matches("g:circle", target, meta) shouldBe true
        matches("creator:x", target, meta) shouldBe true
        matches("circle:x", target, meta) shouldBe true
        matches("parody:series", target, meta) shouldBe true
        matches("p:series", target, meta) shouldBe true
        matches("series:y", target, meta) shouldBe true
        matches("character:char", target, meta) shouldBe true
        matches("c:z", target, meta) shouldBe true
        matches("char:z", target, meta) shouldBe true
        matches("female:futa", target, meta) shouldBe true
        matches("f:futa", target, meta) shouldBe true
        matches("male:mob", target, meta) shouldBe true
        matches("m:mob", target, meta) shouldBe true
        matches("reclass:rewritten", target, meta) shouldBe true
        matches("r:rewritten", target, meta) shouldBe true
        matches("l:english", target, meta) shouldBe true
    }

    @Test
    fun `metadata namespace matches case insensitively`() {
        val target = item()
        val meta = metadata(tags = listOf(tag("Artist", "Foo")))
        matches("artist:FOO", target, meta) shouldBe true
        matches("ARTIST:foo", target, meta) shouldBe true
    }

    @Test
    fun `metadata namespace does not fall back to the manga field`() {
        // For EH, manga.artist is the *group*, and artist tags go to manga.author. `artist:`
        // must not silently match either manga field.
        val target = item(title = "Foobar").let {
            it.copy(
                libraryManga = it.libraryManga.copy(
                    manga = it.libraryManga.manga.copy(ogArtist = "Foo", ogAuthor = "Foo"),
                ),
            )
        }
        matches("artist:foo", target, null) shouldBe false
        matches("artist:foo", target, metadata()) shouldBe false
    }

    @Test
    fun `metadata namespace without metadata does not match`() {
        matches("artist:foo", item(), null) shouldBe false
        matches("artist:foo", item(), metadata()) shouldBe false
    }

    @Test
    fun `negated metadata matches when the tag is absent`() {
        val target = item()
        val with = metadata(tags = listOf(tag("artist", "Foo")))
        val without = metadata(tags = listOf(tag("artist", "Bar")))

        matches("-artist:foo", target, with) shouldBe false
        matches("-artist:foo", target, without) shouldBe true
        // A negated term matches an entry without any metadata at all
        matches("-artist:foo", target, null) shouldBe true
    }

    @Test
    fun `negated metadata does not fall back to the manga field`() {
        // `-artist:foo` must not be cancelled out by a manga whose artist field contains "foo"
        val target = item(title = "Foobar").let {
            it.copy(
                libraryManga = it.libraryManga.copy(
                    manga = it.libraryManga.manga.copy(ogArtist = "Foo"),
                ),
            )
        }
        matches("-artist:foo", target, null) shouldBe true
    }

    @Test
    fun `uploader matches the metadata field or the virtual tag`() {
        val target = item()
        matches("uploader:bob", target, metadata(uploader = "Bob")) shouldBe true
        matches("uploader:bob", target, metadata(uploader = "Alice")) shouldBe false
        matches("uploader:bob", target, metadata(tags = listOf(tag("uploader", "Bob")))) shouldBe true
    }

    @Test
    fun `language field stays the source language`() {
        // item() has sourceLanguage "en"
        matches("lang:en", item(), null) shouldBe true
        matches("language:en", item(), null) shouldBe true
        matches("lang:english", item(), null) shouldBe false
    }

    @Test
    fun `metadata terms combine with ast terms`() {
        val target = item(id = 42L)
        val meta = metadata(tags = listOf(tag("parody", "Series Y")))
        matches("id:42 parody:series", target, meta) shouldBe true
        matches("id:43 parody:series", target, meta) shouldBe false
        matches("id:42 parody:nope", target, meta) shouldBe false
        matches("id:42 || parody:nope", target, meta) shouldBe true
    }

    @Test
    fun `containsMetadataQuery detects metadata namespaces`() {
        QueryNode.from("foo bar").containsMetadataQuery() shouldBe false
        QueryNode.from("id:1").containsMetadataQuery() shouldBe false
        QueryNode.from("artist:foo").containsMetadataQuery() shouldBe true
        QueryNode.from("id:1 || artist:foo").containsMetadataQuery() shouldBe true
        QueryNode.from("-(id:1 && g:circle)").containsMetadataQuery() shouldBe true
    }

    // KUN <--
}
