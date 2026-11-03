package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

// KNS -->
class MangaBakaChapterCountTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun series(status: String, extra: String) = json.decodeFromString<MangaBakaItemResult>(
        """
        {"data":{"id":572621,"cover":{"x250":{"x1":null}},"authors":null,"artists":null,"description":null,
        "published":{"start_date":"2026-01-08"},"status":"$status","type":"manhwa","rating":null,"titles":null,
        $extra "state":"active"}}
        """.trimIndent(),
    ).data

    @Test
    fun `a releasing series reports the chapter count MangaBaka sends as a string`() {
        // as returned for https://mangabaka.org/manhwa/572621 : releasing, "total_chapters":"29"
        val item = series("releasing", """"total_chapters":"29","final_chapter":null,""")

        item.chapterCount() shouldBe 29L
        item.isFinished() shouldBe false
    }

    @Test
    fun `the count is read whatever its json type`() {
        series("releasing", """"total_chapters":29,""").chapterCount() shouldBe 29L
        series("releasing", """"total_chapters":"29.0",""").chapterCount() shouldBe 29L
        series("releasing", """"total_chapters":null,""").chapterCount() shouldBe null
        series("releasing", "").chapterCount() shouldBe null
        series("releasing", """"total_chapters":"",""").chapterCount() shouldBe null
        series("releasing", """"total_chapters":"0",""").chapterCount() shouldBe null
    }

    @Test
    fun `the final chapter stands in when there is no total`() {
        series("completed", """"total_chapters":null,"final_chapter":"112",""").chapterCount() shouldBe 112L
        series("completed", """"total_chapters":"110","final_chapter":"112",""").chapterCount() shouldBe 110L
    }

    @Test
    fun `only a completed series is finished`() {
        series("completed", "").isFinished() shouldBe true
        for (status in listOf("releasing", "hiatus", "upcoming", "unknown", "cancelled")) {
            series(status, "").isFinished() shouldBe false
        }
    }

    @Test
    fun `reaching the count completes a finished series only`() {
        // releasing: reading 29 of 29, or beyond the count it had so far, is not the end
        MangaBakaCompletion.isFinished(seriesCompleted = false, lastChapterRead = 29.0, totalChapters = 29) shouldBe false
        MangaBakaCompletion.isFinished(seriesCompleted = false, lastChapterRead = 31.0, totalChapters = 29) shouldBe false
        // completed: the last chapter (or past it) completes, earlier ones do not
        MangaBakaCompletion.isFinished(seriesCompleted = true, lastChapterRead = 29.0, totalChapters = 29) shouldBe true
        MangaBakaCompletion.isFinished(seriesCompleted = true, lastChapterRead = 30.0, totalChapters = 29) shouldBe true
        MangaBakaCompletion.isFinished(seriesCompleted = true, lastChapterRead = 28.0, totalChapters = 29) shouldBe false
        // an unknown count never completes
        MangaBakaCompletion.isFinished(seriesCompleted = true, lastChapterRead = 50.0, totalChapters = 0) shouldBe false
    }
}
// KNS <--
