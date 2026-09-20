package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibrarySearchParserTest {
    @Test
    fun `numeric fields match full IDs and preserve negative source IDs`() {
        LibrarySearchParser.parse("id:1").single().matches(listOf("10")) shouldBe false
        LibrarySearchParser.parse("id:1").single().matches(listOf("1")) shouldBe true
        LibrarySearchParser.parse("src:-42").single() shouldBe LibrarySearchToken("-42", "source")
        LibrarySearchParser.parse("status:1").single().matches(listOf("61")) shouldBe false
    }

    @Test
    fun `ordinary phrases and quoted terms retain their text`() {
        LibrarySearchParser.parse("one two").map { it.text } shouldBe listOf("one", "two")
        LibrarySearchParser.parse("\"one two\" title:\"three four\"") shouldBe listOf(
            LibrarySearchToken("one two"),
            LibrarySearchToken("three four", "title"),
        )
    }

    @Test
    fun `exclusion exact matching and aliases compose`() {
        LibrarySearchParser.parse("-genre:action NOT src:local ${'$'}title:Test tags:magic circle:team desc:story") shouldBe listOf(
            LibrarySearchToken("action", "genre", excluded = true),
            LibrarySearchToken("local", "source", excluded = true),
            LibrarySearchToken("Test", "title", exact = true),
            LibrarySearchToken("magic", "tag"),
            LibrarySearchToken("team", "group"),
            LibrarySearchToken("story", "description"),
        )
        LibrarySearchToken("Test", exact = true).matches(listOf("testing")) shouldBe false
        LibrarySearchToken("Test", exact = true).matches(listOf("test")) shouldBe true
        LibrarySearchToken("Test").matches(listOf("testing")) shouldBe true
    }

    @Test
    fun `unknown namespaces and unfinished quotes are safe`() {
        LibrarySearchParser.parse("unknown:value \"unfinished phrase").map { it.text } shouldBe listOf("unknown:value", "unfinished phrase")
        listOf("", "-", "NOT", "title:", "\"", ":", "${'$'}", "-title: next").forEach { LibrarySearchParser.parse(it) }
        LibrarySearchParser.parse("\"NOT\" \"-literal\"").map { it.excluded } shouldBe listOf(false, false)
    }
}
