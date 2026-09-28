package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class MangaBakaExternalSourcesTest {
    @Test
    fun `external tracker source bindings preserve string and numeric IDs`() {
        val item = Json.decodeFromString<MangaBakaItem>(
            """
            {"id":583843,"source":{"manga_updates":{"id":"6ol8fx2"},"my_anime_list":{"id":195632}},
            "cover":{"raw":{"url":null},"x150":{"x1":null,"x2":null,"x3":null},"x250":{"x1":null,"x2":null,"x3":null},"x350":{"x1":null,"x2":null,"x3":null}},
            "authors":null,"artists":null,"description":null,"published":{"start_date":null},"rating":null,"total_chapters":null,"titles":null}
            """.trimIndent(),
        )

        item.source?.mangaUpdates?.id shouldBe "6ol8fx2"
        item.source?.myAnimeList?.id shouldBe 195632L
    }
}
