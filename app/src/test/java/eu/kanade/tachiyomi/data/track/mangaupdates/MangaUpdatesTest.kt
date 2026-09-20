package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MangaUpdatesTest {
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String>()
    private var lookupCode = 404
    private var lookup = ""
    private var writeCode = 200
    private var ratingCode = 200
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request
        bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
        val isLookup = request.method == "GET" && "/lists/" in request.url.encodedPath
        val isRating = request.method == "GET" && request.url.encodedPath.endsWith("/rating")
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(
                if (isLookup) {
                    lookupCode
                } else if (isRating) {
                    ratingCode
                } else {
                    writeCode
                },
            )
            .message("test")
            .body(
                (
                    if (isLookup) {
                        lookup
                    } else if (isRating) {
                        "{\"rating\":7.5}"
                    } else {
                        ""
                    }
                    ).toResponseBody(),
            )
            .build()
    }.build()
    private val api = MangaUpdatesApi(Interceptor { it.proceed(it.request()) }, client, Json { ignoreUnknownKeys = true })
    private val service = MangaUpdates(7, api)
    private fun track() = Track.create(7).apply {
        remote_id = 123
        manga_id = 1
        title = "Title"
        tracking_url = ""
    }
    private fun write() = Json.parseToJsonElement(bodies[requests.indexOfFirst { it.method == "POST" }]).jsonArray.single().jsonObject

    @Test
    fun `unread binding writes wish list and does not invent chapter one`() = runTest {
        val bound = service.bind(track(), false)
        requests.map { it.method to it.url.encodedPath } shouldBe listOf("GET" to "/v1/lists/series/123", "POST" to "/v1/lists/series")
        write()["list_id"]!!.jsonPrimitive.content shouldBe "1"
        write()["series"]!!.jsonObject["id"]!!.jsonPrimitive.content shouldBe "123"
        bound.status shouldBe MangaUpdates.WISH_LIST
        bound.last_chapter_read shouldBe 0.0
    }

    @Test
    fun `successful lookup without membership still initializes remote list`() = runTest {
        lookupCode = 200
        lookup = "{\"series\":{\"id\":123}}"
        service.bind(track(), false).status shouldBe MangaUpdates.WISH_LIST
        requests.count { it.url.encodedPath == "/v1/lists/series" && it.method == "POST" } shouldBe 1
    }

    @Test
    fun `existing binding updates same entry preserving status progress and score without duplicate add`() = runTest {
        lookupCode = 200
        lookup = "{\"series\":{\"id\":123},\"list_id\":4,\"status\":{\"chapter\":8}}"
        val bound = service.bind(track(), false)
        bound.status shouldBe MangaUpdates.ON_HOLD_LIST
        bound.last_chapter_read shouldBe 8.0
        bound.score shouldBe 7.5
        requests.filter { it.method == "POST" }.map { it.url.encodedPath } shouldBe listOf("/v1/lists/series/update")
        write()["list_id"]!!.jsonPrimitive.content shouldBe "4"
        write()["status"]!!.jsonObject["chapter"]!!.jsonPrimitive.content shouldBe "8"
        requests.last().method shouldBe "PUT"
        bodies.last() shouldBe "{\"rating\":7.5}"
    }

    @Test
    fun `already read binding initializes reading list`() = runTest {
        service.bind(track(), true).status shouldBe MangaUpdates.READING_LIST
        write()["list_id"]!!.jsonPrimitive.content shouldBe "0"
    }

    @Test
    fun `lookup auth or server errors never masquerade as missing membership`() = runTest {
        for (code in listOf(401, 403, 500)) {
            lookupCode = code
            assertThrows<HttpException> { service.bind(track(), false) }.code shouldBe code
        }
        requests.all { it.method == "GET" } shouldBe true
    }

    @Test
    fun `failed initial remote write propagates without reporting a bound success`() = runTest {
        writeCode = 412
        assertThrows<HttpException> { service.bind(track(), false) }.code shouldBe 412
    }

    @Test
    fun `rating lookup failure propagates rather than clearing score or adding a duplicate`() = runTest {
        lookupCode = 200
        lookup = "{\"series\":{\"id\":123},\"list_id\":1}"
        ratingCode = 500
        assertThrows<HttpException> { service.bind(track(), false) }.code shouldBe 500
        requests.all { it.method == "GET" } shouldBe true
    }

    @Test
    fun `progress status and score edits use list update and rating endpoints without unsupported dates`() = runTest {
        val value = track().apply {
            status = MangaUpdates.WISH_LIST
            last_chapter_read = 12.0
            score = 8.5
        }
        service.update(value, true).status shouldBe MangaUpdates.READING_LIST
        write()["status"]!!.jsonObject["chapter"]!!.jsonPrimitive.content shouldBe "12"
        bodies.last() shouldBe "{\"rating\":8.5}"
        service.supportsReadingDates shouldBe false
        value.score = 0.0
        value.status = MangaUpdates.COMPLETE_LIST
        service.update(value, false).status shouldBe MangaUpdates.COMPLETE_LIST
        requests.last().method shouldBe "DELETE"
    }

    @Test
    fun `invalid placeholder cannot bind`() = runTest {
        assertThrows<IllegalArgumentException> { service.bind(track().apply { remote_id = 0 }, false) }
        requests shouldBe emptyList()
    }
}
