package eu.kanade.tachiyomi.data.webhook

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

// KNS -->
/** Posts one JSON body to one URL; throws on failure. */
fun interface WebhookTransport {
    suspend fun post(url: String, body: String)
}

@ContributesBinding(AppScope::class)
@Inject
class OkHttpWebhookTransport(private val network: NetworkHelper) : WebhookTransport {
    private val client by lazy { network.client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    override suspend fun post(url: String, body: String) {
        withIOContext {
            client.newCall(POST(url, body = body.toRequestBody(jsonMime))).awaitSuccess().close()
        }
    }
}

/**
 * Delivers webhook events. [notify] never blocks or throws: events go into a bounded queue (64, extra
 * events are dropped) that one app-scoped worker drains. Nothing is sent while webhooks or the event
 * are off, in incognito mode, for manga of incognito sources (merged entries included), for manga in an
 * excluded category, or for manga tracked privately. Failures are logged without the URL.
 */
@SingleIn(AppScope::class)
@Inject
class WebhookNotifier(
    private val preferences: WebhookPreferences,
    private val getIncognitoState: GetIncognitoState,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val getMergedMangaById: GetMergedMangaById,
    private val transport: WebhookTransport,
    @AppCoroutineScope scope: CoroutineScope,
) {
    private data class Message(val event: WebhookEvent, val manga: Manga?, val data: Map<String, String>)

    private val queue = Channel<Message>(QUEUE_SIZE)

    init {
        scope.launch {
            for (message in queue) {
                try {
                    if (isEnabled(message.event) && !isSuppressed(message.manga)) send(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("DBG " + e)
                    e.printStackTrace()
                    logFailure("Webhook delivery failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    fun notify(event: WebhookEvent, manga: Manga? = null, data: Map<String, String> = emptyMap()) {
        if (!isEnabled(event)) return
        if (queue.trySend(Message(event, manga, data.toMap())).isFailure) logFailure("Webhook queue full; event dropped")
    }

    /** Sends a test event now, ignoring the event switches; throws when nothing could be delivered. */
    suspend fun sendTest() {
        check(preferences.enabled.get()) { "Webhooks are disabled" }
        check(!getIncognitoState.await(null)) { "Incognito mode is enabled" }
        send(Message(WebhookEvent.TEST, null, mapOf("message" to "Komikku webhook test")))
    }

    private fun isEnabled(event: WebhookEvent) = preferences.enabled.get() && preferences.event(event).get()

    private suspend fun isSuppressed(manga: Manga?): Boolean {
        if (getIncognitoState.await(manga?.source)) return true
        if (manga == null) return false
        if (manga.source == MERGED_SOURCE_ID &&
            getMergedMangaById.await(manga.id).any { getIncognitoState.await(it.source) }
        ) {
            return true
        }
        val excluded = preferences.excludedCategories.get()
        val categoryIds = getCategories.await(manga.id).map { it.id.toString() }.ifEmpty { listOf("0") }
        return categoryIds.any { it in excluded } || getTracks.await(manga.id).any { it.private }
    }

    private suspend fun send(message: Message) {
        val manga = message.manga
        val data = manga?.let { mapOf("manga" to it.title) }.orEmpty() + message.data
        val cover = manga?.thumbnailUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val timestamp = Clock.System.now().toString()
        val endpoints = listOf(
            preferences.discordUrl.get() to WebhookPayload.discord(message.event, data, timestamp, cover),
            preferences.genericUrl.get() to WebhookPayload.generic(message.event, data, timestamp, cover),
        ).filter { it.first.isNotBlank() }
        check(endpoints.isNotEmpty()) { "No webhook URL configured" }
        var failure: Exception? = null
        for ((url, payload) in endpoints) {
            try {
                transport.post(url, payload.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                logFailure("Webhook request failed: ${e.javaClass.simpleName}")
            }
        }
        failure?.let { throw it }
    }

    private fun logFailure(message: String) {
        "WebhookNotifier".xLogE(message)
    }

    private companion object {
        const val QUEUE_SIZE = 64
    }
}
// KNS <--
