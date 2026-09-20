package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaMetadata
import eu.kanade.tachiyomi.data.track.anilist.dto.toAutofillMetadata
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.toAutofillMetadata
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MetadataAutofillTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun mangaBaka(extra: String) = json.decodeFromString<MangaBakaItem>(
        """
        {"id":1,"cover":{"raw":{"url":null},"x150":{"x1":null,"x2":null,"x3":null},"x250":{"x1":null,"x2":null,"x3":null},"x350":{"x1":null,"x2":null,"x3":null}},
        "authors":null,"artists":null,"description":null,"published":{"start_date":null},"type":"manga","rating":null,"total_chapters":null,"titles":null $extra}
    """,
    )

    @ParameterizedTest
    @CsvSource("releasing, 1", "completed, 2", "cancelled, 5", "hiatus, 6", "unknown, NULL", "upcoming, NULL", "ongoing, 1", nullValues = ["NULL"])
    fun `every API publication status reaches autofill through the DTO`(remote: String, local: Long?) {
        val metadata = mangaBaka(""", "status":"$remote", "tags":["Time Travel"]""").toAutofillMetadata(null)
        metadata.status shouldBe local
        metadata.tags shouldBe listOf("Time Travel")
        // The editor only replaces status when metadata supplies a local equivalent.
        (metadata.status ?: SManga.COMPLETED.toLong()) shouldBe (local ?: SManga.COMPLETED.toLong())
    }

    @Test
    fun `MangaBaka genres tags and publication status reach autofill`() {
        val item = mangaBaka(""", "status":"completed", "genres":["action","school_life"], "tags":["Action","Time Travel"]""")
        val metadata = item.toAutofillMetadata(null)
        metadata.tags shouldBe listOf("Action", "School life", "Time Travel")
        metadata.status shouldBe SManga.COMPLETED.toLong()
    }

    @Test
    fun `missing empty or unknown MangaBaka values preserve existing metadata`() {
        listOf("", """, "status":"unknown", "genres":[], "tags":[]""").forEach {
            val metadata = mangaBaka(it).toAutofillMetadata(null)
            metadata.tags shouldBe null
            metadata.status shouldBe null
        }
        mapOf("ongoing" to SManga.ONGOING, "hiatus" to SManga.ON_HIATUS, "cancelled" to SManga.CANCELLED).forEach { (remote, local) ->
            mangaBaka(""", "status":"$remote"""").toAutofillMetadata(null).status shouldBe local.toLong()
        }
    }

    @Test
    fun `AniList existing tags and status mapping is preserved`() {
        val response = json.decodeFromString<ALMangaMetadata>(
            """
            {"data":{"Media":{"id":1,"title":{"userPreferred":"Title"},"coverImage":{"large":"cover"},"description":null,"staff":{"edges":[]},"genres":["Action"],"tags":[{"name":"Time Travel"}],"status":"HIATUS"}}}
        """,
        )
        val metadata = response.data.media.toAutofillMetadata()
        metadata.tags shouldBe listOf("Action", "Time Travel")
        metadata.status shouldBe SManga.ON_HIATUS.toLong()
        response.data.media.copy(genres = null, tags = null, status = null).toAutofillMetadata().let {
            it.tags shouldBe null
            it.status shouldBe null
        }
    }
}
