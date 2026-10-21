package eu.kanade.tachiyomi.data.webhook

import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.domain.connections.service.WebhookPreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks

// KNS -->
class WebhookNotifierTest {
    // Event switches are created per call, so the in-memory store needs them up front
    private val preferences = WebhookPreferences(
        InMemoryPreferenceStore(
            sequenceOf(InMemoryPreferenceStore.InMemoryPreference("pref_webhook_chapter_read", true, false)),
        ),
    )
    private val incognito = mockk<GetIncognitoState>()
    private val categories = mockk<GetCategories>()
    private val tracks = mockk<GetTracks>()
    private val sent = mutableListOf<Pair<String, String>>()
    private val manga = Manga.create().copy(id = 1, source = 5, ogTitle = "Title")

    init {
        coEvery { incognito.await(any()) } returns false
        coEvery { categories.await(1) } returns listOf(Category(7, "Reading", 0, 0, false))
        coEvery { tracks.await(1) } returns emptyList()
        preferences.enabled.set(true)
        preferences.genericUrl.set("https://example.invalid/hook")
    }

    private fun TestScope.notifier() = WebhookNotifier(
        preferences = preferences,
        getIncognitoState = incognito,
        getCategories = categories,
        getTracks = tracks,
        getMergedMangaById = mockk<GetMergedMangaById>(),
        transport = { url, body -> sent += url to body },
        scope = TestScope(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `enabled events are delivered with the manga and data`() = runTest {
        val notifier = notifier()
        notifier.notify(WebhookEvent.CHAPTER_READ, manga, mapOf("chapter" to "Chapter 3"))
        notifier.notify(WebhookEvent.MANGA_ADDED, manga)
        advanceUntilIdle()

        sent.size shouldBe 1
        sent.single().first shouldBe "https://example.invalid/hook"
        val body = kotlinx.serialization.json.Json.parseToJsonElement(sent.single().second).jsonObject
        body["event"]!!.jsonPrimitive.content shouldBe "chapter_read"
        body["data"]!!.jsonObject["chapter"]!!.jsonPrimitive.content shouldBe "Chapter 3"
        body["data"]!!.jsonObject["manga"]!!.jsonPrimitive.content shouldBe "Title"
    }

    @Test
    fun `excluded categories, incognito sources and private tracking suppress events`() = runTest {
        val notifier = notifier()
        preferences.excludedCategories.set(setOf("7"))
        notifier.notify(WebhookEvent.CHAPTER_READ, manga)
        advanceUntilIdle()
        sent shouldBe emptyList()

        preferences.excludedCategories.set(emptySet())
        coEvery { incognito.await(5) } returns true
        notifier.notify(WebhookEvent.CHAPTER_READ, manga)
        advanceUntilIdle()
        sent shouldBe emptyList()
    }

    @Test
    fun `a disabled webhook sends nothing, and the test needs it enabled`() = runTest {
        val notifier = notifier()
        preferences.enabled.set(false)
        notifier.notify(WebhookEvent.CHAPTER_READ, manga)
        advanceUntilIdle()
        sent shouldBe emptyList()
        assertThrows<IllegalStateException> { notifier.sendTest() }
    }

    @Test
    fun `Discord payloads disable mentions and keep the URL out of the body`() {
        val body = WebhookPayload.discord(WebhookEvent.MANGA_FINISHED, mapOf("manga" to "@everyone Title"), "t").toString()
        body shouldContain "\"allowed_mentions\":{\"parse\":[]}"
        body shouldContain "Manga finished"
        body shouldNotContain "example.invalid"
    }
}
// KNS <--
