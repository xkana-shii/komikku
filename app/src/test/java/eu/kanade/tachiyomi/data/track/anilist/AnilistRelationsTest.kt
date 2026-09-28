package eu.kanade.tachiyomi.data.track.anilist

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.SequelPrequelRelation

class AnilistRelationsTest {
    @Test
    fun `structured NOVEL format is filtered without guessing from title or relationship`() {
        val formats = listOf("MANGA", "NOVEL", null)
        val kinds = listOf("PREQUEL", "SEQUEL", "SIDE_STORY")
        val edges = formats.mapIndexed { index, format ->
            """{"relationType":"${kinds[index]}","node":{"id":${index + 1},"type":"MANGA","format":${format?.let { "\"$it\"" } ?: "null"},"title":{"userPreferred":"Novel Hero"}}}"""
        }.joinToString(",")
        val response = Json.parseToJsonElement("""{"data":{"Media":{"relations":{"edges":[$edges]}}}}""").jsonObject
        val entries = response.toRelatedEntries()
        entries.map { it.remoteId } shouldBe listOf(1L, 3L)
        entries.map { it.relation } shouldBe listOf(SequelPrequelRelation.PREQUEL, SequelPrequelRelation.SIDE_STORY)
        entries.map { it.title }.distinct() shouldBe listOf("Novel Hero")
    }
}
