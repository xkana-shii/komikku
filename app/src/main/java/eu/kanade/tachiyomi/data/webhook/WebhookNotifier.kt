package eu.kanade.tachiyomi.data.webhook

import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.domain.connections.service.WebhookPreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import exh.log.xLogE
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.TimeUnit

// KMK --> Bounded best-effort queue. No event bus or synchronous network in user actions.
class WebhookNotifier(
    private val preferences: WebhookPreferences = Injekt.get(),
    private val incognito: GetIncognitoState = Injekt.get(),
    private val categories: GetCategories = Injekt.get(),
    private val tracks: GetTracks = Injekt.get(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val post: suspend (String, String) -> Unit = { url, payload ->
        Injekt.get<NetworkHelper>().client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
            .newCall(POST(url, body = payload.toRequestBody(jsonMime))).awaitSuccess().use { }
    },
    private val logFailure: (String) -> Unit = { "WebhookNotifier".xLogE(it) },
) {
    private data class Message(val event: WebhookEvent, val manga: Manga?, val data: Map<String, String>)
    private val queue = Channel<Message>(64)

    init {
        scope.launch {
            for (message in queue) {
                try {
                    if (!preferences.enabled().get() || !preferences.event(message.event).get() || suppressed(message.manga)) continue
                    send(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logFailure("Webhook delivery failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    fun notify(event: WebhookEvent, manga: Manga? = null, data: Map<String, String> = emptyMap()) {
        try {
            if (!preferences.enabled().get() || !preferences.event(event).get() || incognito.await(manga?.source)) return
            if (queue.trySend(Message(event, manga, data.toMap())).isFailure) logFailure("Webhook queue full; event dropped")
        } catch (e: Exception) {
            logFailure("Webhook enqueue failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun suppressed(manga: Manga?): Boolean {
        if (incognito.await(manga?.source)) return true
        if (manga == null) return false
        if (manga.source == MERGED_SOURCE_ID) {
            val children = Injekt.get<MangaMergeRepository>().getMergedMangaById(manga.id)
            if (children.any { incognito.await(it.source) }) return true
        }
        val categoryIds = categories.await(manga.id).map { it.id.toString() }.ifEmpty { listOf("0") }
        return categoryIds.any { it in preferences.excludedCategories().get() } || tracks.await(manga.id).any { it.private }
    }

    suspend fun sendTest() {
        check(preferences.enabled().get()) { "Webhooks are disabled" }
        check(!incognito.await(null)) { "Incognito mode is enabled" }
        send(Message(WebhookEvent.TEST, null, mapOf("message" to "Komikku webhook test")))
    }

    private suspend fun send(message: Message) {
        val manga = message.manga
        val data = (manga?.let { mapOf("manga" to it.title) }.orEmpty() + message.data)
        val cover = manga?.thumbnailUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val timestamp = Instant.now().toString()
        val endpoints = listOf(
            preferences.discordUrl().get() to WebhookPayload.discord(message.event, data, timestamp, cover),
            preferences.genericUrl().get() to WebhookPayload.generic(message.event, data, timestamp, cover),
        ).filter { it.first.isNotBlank() }
        check(endpoints.isNotEmpty()) { "No webhook URL configured" }
        var failure: Exception? = null
        for ((url, payload) in endpoints) {
            try {
                post(url, payload.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                logFailure("Webhook request failed: ${e.javaClass.simpleName}")
            }
        }
        failure?.let { throw it }
    }
}
// KMK <--
