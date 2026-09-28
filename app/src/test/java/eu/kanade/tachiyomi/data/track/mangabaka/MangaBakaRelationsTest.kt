package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.manga.model.SequelPrequelRelation

class MangaBakaRelationsTest {
    @ParameterizedTest
    @CsvSource("manga,true", "novel,false", "manhwa,true", "manhua,true", "oel,true", "other,true")
    fun `only the novel series type is filtered`(type: String, kept: Boolean) {
        val entry = mangaBakaRelationItem(type).toRelatedEntry("prequel", 1)
        (entry != null) shouldBe kept
        if (kept) {
            entry!!.remoteId shouldBe 7L
            entry.url shouldBe "https://mangabaka.org/7"
            entry.trackerId shouldBe 1L
            entry.title shouldBe "Novel Hero"
            entry.coverUrl shouldBe "https://example.invalid/cover"
            entry.relation shouldBe SequelPrequelRelation.PREQUEL
            entry.localMangaId shouldBe null
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "main", "source", "adaptation", "prequel", "sequel", "parent", "side_story", "spin_off",
            "alternative", "remake", "reboot", "expansion", "character_focus", "series", "compilation",
            "contains", "uncollected", "summary", "crossover", "cameo", "parody", "other",
        ],
    )
    fun `every MangaBaka relationship is preserved independently of the series type`(relation: String) {
        mangaBakaRelationItem("novel").toRelatedEntry(relation, 1) shouldBe null
        for (type in listOf("manga", "manhwa", "manhua", "oel", "other")) {
            mangaBakaRelationItem(type).toRelatedEntry(relation, 1)!!.relation shouldBe
                SequelPrequelRelation.valueOf(relation.uppercase())
        }
    }

    @Test
    fun `mixed relations drop novels while preserving comic order and relationship mapping`() {
        val relations = listOf(
            "prequel" to "novel",
            "prequel" to "manga",
            "sequel" to "manhwa",
            "spin_off" to "novel",
            "spin_off" to "manhua",
        )
        val entries = relations.mapIndexedNotNull { index, (relation, type) ->
            mangaBakaRelationItem(type, index.toLong()).toRelatedEntry(relation, 1)
        }
        entries.map { it.remoteId } shouldBe listOf(1L, 2L, 4L)
        entries.map { it.relation } shouldBe listOf(
            SequelPrequelRelation.PREQUEL,
            SequelPrequelRelation.SEQUEL,
            SequelPrequelRelation.SPIN_OFF,
        )
    }
}

internal fun mangaBakaRelationItem(type: String, id: Long = 7): MangaBakaItem = Json.decodeFromString(
    """
    {"id":$id,"type":"$type",
    "cover":{"raw":{"url":"https://example.invalid/cover"},"x150":{"x1":null,"x2":null,"x3":null},"x250":{"x1":null,"x2":null,"x3":null},"x350":{"x1":null,"x2":null,"x3":null}},
    "authors":null,"artists":null,"description":null,"published":{"start_date":null},"rating":null,"total_chapters":null,
    "titles":[{"language":"en","traits":[],"title":"Novel Hero","is_primary":true}]}
    """.trimIndent(),
)
