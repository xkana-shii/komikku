package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.SequelPrequelRelation

// KNS -->
class MangaBakaRelationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `relationships are parsed and novels are left out`() {
        val series = item(id = 1, type = "manga", relationships = """[{"to_series_id":2,"relation_type":"sequel"}]""")
        series.relationships!!.single().seriesId shouldBe 2L

        val sequel = item(id = 2, type = "manga").toRelatedEntry("sequel", trackerId = 9)!!
        sequel.relation shouldBe SequelPrequelRelation.SEQUEL
        sequel.url shouldBe "https://mangabaka.org/2"
        sequel.remoteId shouldBe 2L
        sequel.trackerId shouldBe 9L
        sequel.title shouldBe "Series 2"

        item(id = 3, type = "novel").toRelatedEntry("side_story", trackerId = 9) shouldBe null
    }

    @Test
    fun `relation names are matched loosely`() {
        SequelPrequelRelation.from("side_story") shouldBe SequelPrequelRelation.SIDE_STORY
        SequelPrequelRelation.from("spin-off") shouldBe SequelPrequelRelation.SPIN_OFF
        SequelPrequelRelation.from("Main Story") shouldBe SequelPrequelRelation.MAIN_STORY
        SequelPrequelRelation.from("anything else") shouldBe SequelPrequelRelation.OTHER
    }

    private fun item(id: Long, type: String, relationships: String? = null) = json.decodeFromString<MangaBakaItemResult>(
        """
        {"data":{"id":$id,"cover":{"x250":{"x1":null}},"authors":[],"artists":[],"description":null,
        "published":{"start_date":null},"status":"releasing","type":"$type","rating":null,
        "titles":[{"language":"en","traits":[],"title":"Series $id","is_primary":true}]
        ${relationships?.let { ""","relationships_v2":$it""" }.orEmpty()}}}
        """.trimIndent(),
    ).data
}
// KNS <--
