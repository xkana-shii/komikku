package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.toRelatedEntries
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.toRelatedEntry
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.dto.MangaDataDto
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.domain.manga.model.isNovelFormat
import eu.kanade.domain.manga.model.isNovelRelation as isNovelSourceRelation
import exh.md.dto.isNovelRelation as isNovelMangaDexRelation

class RelationProvidersTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val novels = listOf("NOVEL", "LIGHT_NOVEL", "Novel", "Light Novel", "light-novel", "web_novel")
    private val comics = listOf("manga", "manhwa", "manhua", "webtoon", "ONE_SHOT", "unknown", "")

    @Test
    fun `explicit novel formats are normalized while comic and missing formats remain`() {
        novels.forEach { isNovelFormat(it) shouldBe true }
        (comics + listOf(null, "Adapted From Novel", "Novel Adaptation")).forEach { isNovelFormat(it) shouldBe false }
    }

    @Test
    fun `AniList mixed relations retain comic sequel prequel and related order`() {
        val formats = listOf("MANGA", "NOVEL", "ONE_SHOT", "LIGHT_NOVEL", null)
        val kinds = listOf("SEQUEL", "SEQUEL", "PREQUEL", "SOURCE", "SIDE_STORY")
        val edges = formats.mapIndexed { index, format ->
            """{"relationType":"${kinds[index]}","node":{"id":${index + 1},"type":"MANGA","format":${format?.let { "\"$it\"" } ?: "null"},"title":{"userPreferred":"Novel Hero"}}}"""
        }.joinToString(",")
        val response = json.parseToJsonElement("""{"data":{"Media":{"relations":{"edges":[$edges]}}}}""").jsonObject
        val entries = response.toRelatedEntries()
        entries.map { it.remoteId } shouldBe listOf(1L, 3L, 5L)
        entries.map { it.relation } shouldBe listOf(SequelPrequelRelation.SEQUEL, SequelPrequelRelation.PREQUEL, SequelPrequelRelation.SIDE_STORY)
        entries.map { it.title }.distinct() shouldBe listOf("Novel Hero")
        entries.last().mediaFormat shouldBe null
    }

    @Test
    fun `MangaBaka uses series type and accepts missing format without dropping comics`() {
        novels.forEach { mangaBaka(it).toRelatedEntry("SEQUEL", 1) shouldBe null }
        for (type in comics + listOf(null)) {
            val entry = mangaBaka(type).toRelatedEntry("PREQUEL", 1)!!
            entry.relation shouldBe SequelPrequelRelation.PREQUEL
            entry.remoteId shouldBe 7L
        }
    }

    @Test
    fun `MangaDex filters explicit format tags without confusing adaptations or unknown formats`() {
        novels.forEach { mangaDex(it, "format").isNovelMangaDexRelation() shouldBe true }
        (comics + "Adaptation" + "Adapted From Novel").forEach { mangaDex(it, "format").isNovelMangaDexRelation() shouldBe false }
        mangaDex("Novel", "theme").isNovelMangaDexRelation() shouldBe false
        mangaDex("Novel", null).isNovelMangaDexRelation() shouldBe false
    }

    @Test
    fun `source related results remove explicit novel tags but never guess from titles`() {
        val items = (novels + comics + listOf(null, "Adapted From Novel")).map { format ->
            SManga.create().apply {
                title = "Novel Hero"
                genre = format
            }
        }
        items.filterNot { it.isNovelSourceRelation() }.map { it.genre } shouldBe comics + listOf(null, "Adapted From Novel")
    }

    private fun mangaBaka(type: String?) = json.decodeFromString<MangaBakaItem>(
        """
        {"id":7,"cover":{"raw":{"url":null},"x150":{"x1":null,"x2":null,"x3":null},"x250":{"x1":null,"x2":null,"x3":null},"x350":{"x1":null,"x2":null,"x3":null}},
        "authors":null,"artists":null,"description":null,"published":{"start_date":null},"rating":null,"total_chapters":null,"titles":null${type?.let { ",\"type\":\"$it\"" }.orEmpty()}}
        """.trimIndent(),
    )

    private fun mangaDex(format: String, group: String?) = json.decodeFromString<MangaDataDto>(
        """
        {"id":"entry","type":"manga","relationships":[],"attributes":{
        "title":{"en":"Novel Hero"},"altTitles":[],"description":{},"links":null,"originalLanguage":"en",
        "lastVolume":null,"lastChapter":null,"contentRating":null,"publicationDemographic":null,"status":null,"year":null,
        "tags":[{"id":"tag","attributes":{"name":{"en":"$format"}${group?.let { ",\"group\":\"$it\"" }.orEmpty()}}}]}}
        """.trimIndent(),
    )
}
