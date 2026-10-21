package mihon.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class LibraryFieldQueryTest {

    @Test
    fun `category, tracker and status terms become library field nodes`() {
        QueryNode.from("cat:Reading") shouldBe LibraryFieldQueryNode(LibraryField.CATEGORY, "Reading", negated = false)
        QueryNode.from("category:5") shouldBe LibraryFieldQueryNode(LibraryField.CATEGORY, "5", negated = false)
        QueryNode.from("-tracker:AniList") shouldBe LibraryFieldQueryNode(LibraryField.TRACKER, "AniList", negated = true)
        QueryNode.from("STATUS:ongoing") shouldBe LibraryFieldQueryNode(LibraryField.STATUS, "ongoing", negated = false)
    }

    @Test
    fun `existing fields and metadata namespaces keep their meaning`() {
        QueryNode.from("title:cat") shouldBe FieldQueryNode(MangaField.TITLE, "cat", negated = false)
        QueryNode.from("c:cat") shouldBe MetadataQueryNode("character", "cat", negated = false)
        QueryNode.from("cats") shouldBe GeneralQueryNode("cats", negated = false)
    }

    @Test
    fun `numbers match ids exactly, text matches names`() {
        val node = LibraryFieldQueryNode(LibraryField.CATEGORY, "5", negated = false)
        node.matches(ids = listOf(5L), names = emptyList()) shouldBe true
        node.matches(ids = listOf(15L), names = listOf("Reading")) shouldBe false
        node.matches(ids = listOf(15L), names = listOf("Top 50")) shouldBe true

        val text = LibraryFieldQueryNode(LibraryField.STATUS, "going", negated = false)
        text.matches(ids = listOf(1L), names = listOf("Ongoing")) shouldBe true
        text.copy(negated = true).matches(ids = listOf(1L), names = listOf("Ongoing")) shouldBe false
        text.copy(negated = true).matches(ids = emptyList(), names = emptyList()) shouldBe true
    }
}
// KNS <--
