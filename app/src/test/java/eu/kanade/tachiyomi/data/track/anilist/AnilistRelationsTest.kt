package eu.kanade.tachiyomi.data.track.anilist

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.SequelPrequelRelation

// KNS -->
class AnilistRelationsTest {

    @Test
    fun `novels and non-manga media are left out by format and type`() {
        val edges = listOf(
            edge(id = 1, relation = "PREQUEL", format = "MANGA"),
            edge(id = 2, relation = "SEQUEL", format = "NOVEL"),
            edge(id = 3, relation = "SIDE_STORY", format = null),
            edge(id = 4, relation = "ADAPTATION", type = "ANIME"),
        )
        val entries = edges.toRelatedEntries(trackerId = 2)
        entries.map { it.remoteId } shouldBe listOf(1L, 3L)
        entries.map { it.relation } shouldBe listOf(SequelPrequelRelation.PREQUEL, SequelPrequelRelation.SIDE_STORY)
        entries.map { it.trackerId }.distinct() shouldBe listOf(2L)
    }

    @Test
    fun `the first non-blank title is used and a missing site URL falls back to AniList`() {
        val entry = listOf(
            edge(id = 7, relation = "CHARACTER", titles = listOf(null, " ", "Romaji"), siteUrl = null),
        ).toRelatedEntries(trackerId = 2).single()
        entry.title shouldBe "Romaji"
        entry.url shouldBe "https://anilist.co/manga/7"
        entry.relation shouldBe SequelPrequelRelation.OTHER
        listOf(edge(id = 8, relation = "PREQUEL", titles = listOf(null, null, null)))
            .toRelatedEntries(trackerId = 2) shouldBe emptyList()
    }

    private fun edge(
        id: Int,
        relation: String,
        type: String = "MANGA",
        format: String? = "MANGA",
        titles: List<String?> = listOf("Title $id"),
        siteUrl: String? = "https://anilist.co/manga/$id/x",
    ) = AnilistRelationEdge(relation, id, type, format, titles, siteUrl, coverUrl = null)
}
// KNS <--
