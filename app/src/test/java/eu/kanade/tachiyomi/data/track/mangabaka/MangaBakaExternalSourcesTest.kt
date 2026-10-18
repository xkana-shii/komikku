package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class MangaBakaExternalSourcesTest {

    @Test
    fun `MangaBaka detail responses retain relation metadata`() {
        val item = Json.decodeFromString<MangaBakaItem>(
            """
            {
                "id": 583843,
                "status": "ongoing",
                "type": "manhwa",
                "merged_with": 583000,
                "relationships_v2": [{"to_series_id": 123, "relation_type": "sequel"}],
                "cover": {
                    "x250": {
                        "x1": null
                    }
                },
                "authors": null,
                "artists": null,
                "description": null,
                "published": {
                    "start_date": null
                },
                "rating": null,
                "titles": null
            }
            """.trimIndent(),
        )

        item.mergedWith shouldBe 583000L
        item.relationships?.single()?.seriesId shouldBe 123L
    }

    @Test
    fun `missing relation metadata remains optional`() {
        val item = Json.decodeFromString<MangaBakaItem>(
            """
            {
                "id": 583843,
                "status": "ongoing",
                "type": "manhwa",
                "cover": {
                    "x250": {
                        "x1": null
                    }
                },
                "authors": null,
                "artists": null,
                "description": null,
                "published": {
                    "start_date": null
                },
                "rating": null,
                "titles": null
            }
            """.trimIndent(),
        )

        item.relationships shouldBe null
    }
}
