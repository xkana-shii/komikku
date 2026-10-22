package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaMetadata
import eu.kanade.tachiyomi.data.track.anilist.dto.toAutofillMetadata
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaApi
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaInterceptor
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.toAutofillMetadata
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.MURecord
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.muPublicationStatus
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.toAutofillMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.autofillEligible
import eu.kanade.tachiyomi.data.track.model.autofillStatusSelection
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.data.track.model.hasAutofillData
import eu.kanade.tachiyomi.data.track.model.mergeAutofillTags
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.dto.toAutofillMetadata
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.htmlDecode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.parser.Parser
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import kotlin.coroutines.Continuation

// KNS -->
/**
 * Metadata autofill for MangaBaka, AniList, MyAnimeList and MangaUpdates: provider responses (shaped
 * like the real APIs) go through the same DTOs and conversions the trackers use.
 */
class MetadataAutofillTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // MangaBaka ----------------------------------------------------------------------------------

    private fun mangaBakaSeries(id: Long, extra: String = "") = """
        {"id":$id,"state":"active","merged_with":null,
         "title":"Sousou no Frieren","native_title":"葬送のフリーレン",
         "titles":[{"language":"en","traits":["official"],"title":"Frieren: Beyond Journey's End","is_primary":true}],
         "cover":{"raw":{"url":"https://cdn.mangabaka.org/raw/$id.jpg"},"x150":{"x1":null},
                  "x250":{"x1":"https://cdn.mangabaka.org/250/$id.jpg","x2":null,"x3":null},"x350":{"x1":null}},
         "authors":["Yamada Kanehito"],"artists":["Abe Tsukasa"],
         "description":"The adventure is over but life goes on for an elf mage &amp; her memories.",
         "published":{"start_date":"2020-04-28","end_date":null},
         "type":"manga","rating":89.3,"total_chapters":"140",
         "source":{"my_anime_list":{"id":126287},"manga_updates":{"id":"e9hm5nm"}}
         $extra}
    """.trimIndent()

    private fun mangaBaka(extra: String) = json.decodeFromString<MangaBakaItem>(mangaBakaSeries(1, extra))

    @ParameterizedTest
    @CsvSource(
        "releasing, 1",
        "ongoing, 1",
        "completed, 2",
        "cancelled, 5",
        "hiatus, 6",
        "upcoming, NULL",
        "unknown, NULL",
        nullValues = ["NULL"],
    )
    fun `MangaBaka publication statuses map to local statuses`(remote: String, local: Long?) {
        mangaBaka(""","status":"$remote"""").toAutofillMetadata(null).status shouldBe local
    }

    @Test
    fun `MangaBaka genres become readable tags, followed by tags, without case duplicates`() {
        val metadata = mangaBaka(
            ""","status":"releasing","genres":["adventure","slice_of_life","fantasy"],
               "tags":["Fantasy","Elf","Time Skip"," ",""]""",
        ).toAutofillMetadata("Description")
        metadata.tags shouldBe listOf("Adventure", "Slice of life", "Fantasy", "Elf", "Time Skip")
        metadata.status shouldBe SManga.ONGOING.toLong()
        metadata.title shouldBe "Frieren: Beyond Journey's End"
        metadata.authors shouldBe "Yamada Kanehito"
        metadata.artists shouldBe "Abe Tsukasa"
        // The raw image wins over the 250px one; without a raw image the 250px one is used.
        metadata.thumbnailUrl shouldBe "https://cdn.mangabaka.org/raw/1.jpg"
        json.decodeFromString<MangaBakaItem>(mangaBakaSeries(1).replace(Regex(""""raw":\{"url":"[^"]*"\},"""), ""))
            .toAutofillMetadata(null).thumbnailUrl shouldBe "https://cdn.mangabaka.org/250/1.jpg"
    }

    @Test
    fun `missing or empty MangaBaka values leave the editor's metadata alone`() {
        listOf("", ""","status":null,"genres":[],"tags":[]""", ""","status":"","genres":null""").forEach { extra ->
            mangaBaka(extra).toAutofillMetadata(null).let {
                it.tags shouldBe null
                it.status shouldBe null
                it.description shouldBe null
            }
        }
    }

    @Test
    fun `MangaBaka fetches series metadata, following merges, with only GET requests`() = runBlocking<Unit> {
        val server = FakeServer(
            "/v1/series/10" to mangaBakaSeries(10, ""","state":"merged","merged_with":20,"status":"hiatus""""),
            "/v1/series/20" to mangaBakaSeries(20, ""","status":"completed","genres":["drama"],"tags":["Elf"]"""),
        )
        val metadata = mangaBakaApi(server).getMangaMetadata(track(remoteId = 10))

        metadata.remoteId shouldBe 20
        metadata.status shouldBe SManga.COMPLETED.toLong()
        metadata.tags shouldBe listOf("Drama", "Elf")
        metadata.description shouldBe "The adventure is over but life goes on for an elf mage & her memories."
        // Autofill reads series metadata only: no library entry is read or written.
        server.requests shouldBe listOf("GET /v1/series/10", "GET /v1/series/20")
    }

    @Test
    fun `missing, deleted and cyclically merged MangaBaka series are errors, not empty metadata`() = runBlocking<Unit> {
        val server = FakeServer(
            "/v1/series/30" to mangaBakaSeries(30, ""","state":"deleted""""),
            "/v1/series/40" to mangaBakaSeries(40, ""","state":"merged","merged_with":41"""),
            "/v1/series/41" to mangaBakaSeries(41, ""","state":"merged","merged_with":40"""),
        )
        val api = mangaBakaApi(server)
        shouldThrow<IllegalStateException> { api.getMangaMetadata(track(remoteId = 30)) }.message shouldContain "deleted"
        shouldThrow<IllegalStateException> { api.getMangaMetadata(track(remoteId = 99)) }.message shouldContain "does not exist"
        shouldThrow<IllegalStateException> { api.getMangaMetadata(track(remoteId = 40)) }.message shouldContain "cycle"
    }

    @Test
    fun `the four trackers implement metadata and declare the capability`() {
        listOf(MangaBaka::class.java, Anilist::class.java, MyAnimeList::class.java, MangaUpdates::class.java).forEach {
            // Declared on the tracker itself, so BaseTracker's NotImplementedError is never reached.
            it.getDeclaredMethod("getMangaMetadata", Track::class.java, Continuation::class.java)
            it.getDeclaredMethod("getSupportsMangaMetadata").returnType shouldBe Boolean::class.javaPrimitiveType
        }
    }

    // AniList ------------------------------------------------------------------------------------

    @Test
    fun `AniList genres, tag names and publication status reach autofill`() {
        val response = json.decodeFromString<ALMangaMetadata>(
            """
            {"data":{"Media":{"id":30002,"title":{"userPreferred":"Berserk"},
              "coverImage":{"large":"https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx30002.jpg"},
              "description":"His name is Guts, the Black Swordsman.<br><br>(Source: Dark Horse)",
              "staff":{"edges":[{"role":"Story & Art","id":1,"node":{"name":{"full":"Kentarou Miura",
                "userPreferred":"Kentarou Miura","native":"三浦建太郎"}}}]},
              "genres":["Action","Adventure","Drama","Fantasy"],
              "tags":[{"name":"Dark Fantasy"},{"name":"Gore"},{"name":"action"}],
              "status":"HIATUS"}}}
            """.trimIndent(),
        )
        val metadata = response.data.media.toAutofillMetadata()
        metadata.tags shouldBe listOf("Action", "Adventure", "Drama", "Fantasy", "Dark Fantasy", "Gore")
        metadata.status shouldBe SManga.ON_HIATUS.toLong()
        metadata.authors shouldBe "Kentarou Miura"
        metadata.artists shouldBe "Kentarou Miura"

        val media = response.data.media
        mapOf("RELEASING" to SManga.ONGOING, "FINISHED" to SManga.COMPLETED, "CANCELLED" to SManga.CANCELLED)
            .forEach { (remote, local) -> media.copy(status = remote).toAutofillMetadata().status shouldBe local.toLong() }
        media.copy(status = "NOT_YET_RELEASED").toAutofillMetadata().status shouldBe null
        media.copy(genres = null, tags = null, status = null).toAutofillMetadata().let {
            it.tags shouldBe null
            it.status shouldBe null
        }
    }

    // MyAnimeList --------------------------------------------------------------------------------

    private fun malResponse(status: String?) = """
        {"id":2,"title":"Berserk",
         "main_picture":{"medium":"https://cdn.myanimelist.net/images/manga/1/157897.jpg",
                         "large":"https://cdn.myanimelist.net/images/manga/1/157897l.jpg"},
         "synopsis":"Guts, a former mercenary now known as the \"Black Swordsman\", is out for revenge.",
         "genres":[{"id":1,"name":"Action"},{"id":2,"name":"Adventure"},{"id":58,"name":"Gore"},
                   {"id":41,"name":"Seinen"},{"id":1,"name":"action"}],
         ${status?.let { "\"status\":\"$it\"," }.orEmpty()}
         "my_list_status":{"status":"reading","is_rereading":false,"num_volumes_read":0,
                           "num_chapters_read":350,"score":10,"updated_at":"2024-06-01T00:00:00+00:00"},
         "authors":[{"node":{"id":1868,"first_name":"Kentarou","last_name":"Miura"},"role":"Story & Art"},
                    {"node":{"id":49592,"first_name":"","last_name":"Studio Gaga"},"role":"Art"}]}
    """.trimIndent()

    @ParameterizedTest
    @CsvSource(
        "currently_publishing, 1",
        "finished, 2",
        "discontinued, 5",
        "on_hiatus, 6",
        "not_yet_published, NULL",
        "something_new, NULL",
        nullValues = ["NULL"],
    )
    fun `MyAnimeList publication statuses map to local statuses, ignoring my_list_status`(remote: String, local: Long?) {
        json.decodeFromString<MALMangaMetadata>(malResponse(remote)).toAutofillMetadata().status shouldBe local
    }

    @Test
    fun `MyAnimeList genres become tags and credits keep their roles`() {
        val metadata = json.decodeFromString<MALMangaMetadata>(malResponse("currently_publishing")).toAutofillMetadata()
        metadata.tags shouldBe listOf("Action", "Adventure", "Gore", "Seinen")
        metadata.authors shouldBe "Kentarou Miura"
        metadata.artists shouldBe "Studio Gaga"
        metadata.thumbnailUrl shouldBe "https://cdn.myanimelist.net/images/manga/1/157897l.jpg"

        json.decodeFromString<MALMangaMetadata>(malResponse(null).replace(Regex(""""genres":\[[^\]]*],"""), ""))
            .toAutofillMetadata().let {
                it.tags shouldBe null
                it.status shouldBe null
            }
    }

    // MangaUpdates -------------------------------------------------------------------------------

    private fun muResponse(status: String?, extra: String = "") = """
        {"series_id":55665373040,"title":"Solo Leveling","url":"https://www.mangaupdates.com/series/pb8uwds/solo-leveling",
         "associated":[{"title":"Na Honjaman Level-Up"}],
         "description":"10 years ago, after &quot;the Gate&quot; that connected the real world with the monster world opened...",
         "image":{"url":{"original":"https://cdn.mangaupdates.com/image/i355448.jpg",
                         "thumb":"https://cdn.mangaupdates.com/image/thumb/i355448.jpg"},"height":350,"width":250},
         "type":"Manhwa","year":"2018","bayesian_rating":8.55,"rating_votes":5123,"latest_chapter":200,
         "genres":[{"genre":"Action"},{"genre":"Adventure"},{"genre":"Fantasy"},{"genre":"Shounen"}],
         "categories":[
           {"series_id":55665373040,"category":"Dungeons","votes":30,"votes_plus":30,"votes_minus":0,"added_by":1},
           {"series_id":55665373040,"category":"action","votes":5,"votes_plus":5,"votes_minus":0,"added_by":2},
           {"series_id":55665373040,"category":"Strong Lead","votes":-2,"votes_plus":1,"votes_minus":3,"added_by":3}],
         ${status?.let { "\"status\":${json.encodeToString(String.serializer(), it)}," }.orEmpty()}
         "licensed":true,"completed":true,
         "authors":[{"name":"Chugong","author_id":1,"type":"Author"},
                    {"name":"DUBU (REDICE STUDIO)","author_id":2,"type":"Artist"}]
         $extra}
    """.trimIndent()

    @Test
    fun `MangaUpdates genres and up-voted categories become tags, status comes from the origin status`() {
        val metadata = json.decodeFromString<MURecord>(muResponse("200 Chapters (Complete)\n4 Side Stories (Complete)"))
            .toAutofillMetadata()
        metadata.tags shouldBe listOf("Action", "Adventure", "Fantasy", "Shounen", "Dungeons")
        metadata.status shouldBe SManga.COMPLETED.toLong()
        metadata.title shouldBe "Solo Leveling"
        metadata.description shouldBe
            "10 years ago, after \"the Gate\" that connected the real world with the monster world opened..."
        metadata.authors shouldBe "Chugong"
        metadata.artists shouldBe "DUBU (REDICE STUDIO)"
    }

    @Test
    fun `MangaUpdates completed flag (completely scanlated) is not the publication status`() {
        json.decodeFromString<MURecord>(muResponse("17 Volumes (Ongoing)")).toAutofillMetadata().status shouldBe
            SManga.ONGOING.toLong()
        json.decodeFromString<MURecord>(muResponse(null)).toAutofillMetadata().status shouldBe null
    }

    @ParameterizedTest
    @CsvSource(
        "'13 Volumes (Ongoing)', 1",
        "'Complete', 2",
        "'5 Volumes (Complete)\n Season 2 (Ongoing)', 1",
        "'3 Volumes (Hiatus)', 6",
        "'Discontinued', 5",
        "'2 Volumes (Cancelled)', 5",
        "'', NULL",
        "'N/A', NULL",
        nullValues = ["NULL"],
    )
    fun `MangaUpdates status texts map to local statuses`(text: String, local: Long?) {
        muPublicationStatus(text.replace("\\n", "\n")) shouldBe local
    }

    @Test
    fun `a MangaUpdates record without genres or categories leaves tags alone`() {
        json.decodeFromString<MURecord>("""{"series_id":1,"title":"T","authors":[]}""").toAutofillMetadata().let {
            it.tags shouldBe null
            it.status shouldBe null
            it.authors shouldBe null
        }
    }

    // Editor -------------------------------------------------------------------------------------

    @Test
    fun `fetched tags are added to the editor's tags, which are kept as typed`() {
        mergeAutofillTags(listOf("my tag", "Fantasy"), listOf("fantasy", "Elf", "My Tag", "Drama")) shouldBe
            listOf("my tag", "Fantasy", "Elf", "Drama")
        mergeAutofillTags(listOf("my tag"), null) shouldBe listOf("my tag")
        autofillTags(listOf(" Action ", "action", null, "", "Drama")) shouldBe listOf("Action", "Drama")
        autofillTags(emptyList()) shouldBe null
    }

    @Test
    fun `a fetched status selects the editor's status entry, unknown ones keep the selection`() {
        listOf(SManga.ONGOING, SManga.COMPLETED, SManga.LICENSED, SManga.PUBLISHING_FINISHED, SManga.CANCELLED, SManga.ON_HIATUS)
            .map { autofillStatusSelection(it.toLong()) } shouldBe listOf(1, 2, 3, 4, 5, 6)
        autofillStatusSelection(null) shouldBe null
        autofillStatusSelection(SManga.UNKNOWN.toLong()) shouldBe null
    }

    @Test
    fun `metadata without anything to apply is not reported as a successful autofill`() {
        TrackMangaMetadata(remoteId = 1).hasAutofillData() shouldBe false
        TrackMangaMetadata(remoteId = 1, title = " ", tags = emptyList(), status = SManga.UNKNOWN.toLong())
            .hasAutofillData() shouldBe false
        TrackMangaMetadata(tags = listOf("Elf")).hasAutofillData() shouldBe true
        TrackMangaMetadata(status = SManga.ON_HIATUS.toLong()).hasAutofillData() shouldBe true
    }

    @Test
    fun `autofill offers logged-in trackers with metadata, so several can be chosen from`() {
        val mangaBaka = tracker(supports = true, loggedIn = true)
        val anilist = tracker(supports = true, loggedIn = true)
        val loggedOut = tracker(supports = true, loggedIn = false)
        val komga = tracker(supports = false, loggedIn = true)
        listOf("mb" to mangaBaka, "al" to anilist, "mal" to loggedOut, "komga" to komga).autofillEligible()
            .map { it.first } shouldBe listOf("mb", "al")
    }

    // Helpers ------------------------------------------------------------------------------------

    private fun tracker(supports: Boolean, loggedIn: Boolean) = mockk<Tracker> {
        every { supportsMangaMetadata } returns supports
        every { isLoggedIn } returns loggedIn
    }

    private fun track(remoteId: Long) = Track(
        id = 1,
        mangaId = 1,
        trackerId = 7,
        remoteId = remoteId,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0,
        status = 1,
        score = 0.0,
        remoteUrl = "",
        startDate = null,
        finishDate = null,
        private = false,
    )

    private fun mangaBakaApi(server: FakeServer) = MangaBakaApi(
        trackerId = 7,
        baseClient = OkHttpClient.Builder().addInterceptor(server::respond).build(),
        interceptor = mockk<MangaBakaInterceptor>(relaxed = true),
    )

    /** Answers requests by path from canned JSON (404 otherwise) and records them. */
    private class FakeServer(vararg routes: Pair<String, String>) {
        private val routes = routes.toMap()
        val requests = mutableListOf<String>()

        fun respond(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            requests += "${request.method} ${request.url.encodedPath}"
            val body = routes[request.url.encodedPath]
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404)
                .message(if (body != null) "OK" else "Not Found")
                .body(("""{"status":200,"data":$body}""".takeIf { body != null } ?: "{}").toResponseBody(JSON_MEDIA))
                .build()
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        private const val STRING_EXTENSIONS = "eu.kanade.tachiyomi.util.lang.StringExtensionsKt"

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Injekt.addSingletonFactory {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }
            // htmlDecode uses android.text.Html, which unit tests do not have; entities are what matter here.
            mockkStatic(STRING_EXTENSIONS)
            every { any<String>().htmlDecode() } answers { Parser.unescapeEntities(firstArg<String>(), false) }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() = unmockkStatic(STRING_EXTENSIONS)
    }
}
// KNS <--
