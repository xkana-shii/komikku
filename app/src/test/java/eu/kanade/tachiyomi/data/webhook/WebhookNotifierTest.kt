package eu.kanade.tachiyomi.data.webhook

import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.domain.connections.service.WebhookPreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks

@OptIn(ExperimentalCoroutinesApi::class)
class WebhookNotifierTest {
    private val preferences = mockk<WebhookPreferences>(relaxed = true)
    private val incognito = mockk<GetIncognitoState>()
    private val categories = mockk<GetCategories>()
    private val tracks = mockk<GetTracks>()

    init {
        every { preferences.enabled().get() } returns true
        every { preferences.event(any()).get() } returns true
        every { preferences.genericUrl().get() } returns "https://example.invalid/generic"
        every { preferences.discordUrl().get() } returns "https://example.invalid/discord"
        every { preferences.excludedCategories().get() } returns emptySet()
        every { incognito.await(any()) } returns false
        coEvery { categories.await(any<Long>()) } returns emptyList()
        coEvery { tracks.await(any<Long>()) } returns emptyList()
    }

    @Test
    fun `master event and incognito switches suppress delivery`() = runTest {
        val sent = mutableListOf<String>()
        val notifier = WebhookNotifier(preferences, incognito, categories, tracks, backgroundScope, { _, body -> sent += body }, {})
        every { preferences.enabled().get() } returns false
        notifier.notify(WebhookEvent.CHAPTER_READ)
        testScheduler.runCurrent()
        every { preferences.enabled().get() } returns true
        every { preferences.event(any()).get() } returns false
        notifier.notify(WebhookEvent.CHAPTER_READ)
        testScheduler.runCurrent()
        every { preferences.event(any()).get() } returns true
        every { incognito.await(any()) } returns true
        notifier.notify(WebhookEvent.CHAPTER_READ, Manga.create())
        testScheduler.runCurrent()
        sent shouldBe emptyList()
    }

    @Test
    fun `queued events recheck privacy and excluded default category`() = runTest {
        val sent = mutableListOf<String>()
        val notifier = WebhookNotifier(preferences, incognito, categories, tracks, backgroundScope, { _, body -> sent += body }, {})
        notifier.notify(WebhookEvent.CHAPTER_READ, Manga.create())
        every { incognito.await(any()) } returns true
        testScheduler.runCurrent()
        every { incognito.await(any()) } returns false
        every { preferences.excludedCategories().get() } returns setOf("0")
        notifier.notify(WebhookEvent.CHAPTER_READ, Manga.create())
        testScheduler.runCurrent()
        sent shouldBe emptyList()
    }

    @Test
    fun `one failed endpoint does not prevent the other endpoint or next event`() = runTest {
        val sent = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val notifier = WebhookNotifier(preferences, incognito, categories, tracks, backgroundScope, { url, body ->
            if (url.endsWith("discord")) error("secret URL must not be logged")
            sent += body
        }, failures::add)
        repeat(2) { notifier.notify(WebhookEvent.BACKUP_CREATED) }
        testScheduler.runCurrent()
        sent.size shouldBe 2
        failures.isNotEmpty() shouldBe true
        failures.any { "secret" in it } shouldBe false
    }

    @Test
    fun `private tracker entries are suppressed independently of incognito`() = runTest {
        val sent = mutableListOf<String>()
        coEvery { tracks.await(any<Long>()) } returns listOf(mockk { every { private } returns true })
        val notifier = WebhookNotifier(preferences, incognito, categories, tracks, backgroundScope, { _, body -> sent += body }, {})
        notifier.notify(WebhookEvent.CHAPTER_READ, Manga.create())
        testScheduler.runCurrent()
        sent shouldBe emptyList()
    }

    @Test
    fun `test request sends generic object and Discord embed without mentions`() = runTest {
        val sent = mutableMapOf<String, String>()
        val notifier = WebhookNotifier(preferences, incognito, categories, tracks, backgroundScope, { url, body -> sent[url] = body }, {})
        notifier.sendTest()
        val generic = Json.parseToJsonElement(sent.getValue("https://example.invalid/generic")).jsonObject
        generic.getValue("event").jsonPrimitive.content shouldBe "test"
        generic.getValue("data").jsonObject.getValue("message").jsonPrimitive.content shouldBe "Komikku webhook test"
        val discord = Json.parseToJsonElement(sent.getValue("https://example.invalid/discord")).jsonObject
        discord.getValue("embeds").jsonArray.size shouldBe 1
        discord.getValue("allowed_mentions").jsonObject.getValue("parse").jsonArray.size shouldBe 0
    }
}
