package eu.kanade.tachiyomi.data.webhook

import eu.kanade.domain.connections.service.WebhookEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// KNS -->
/** Webhook request bodies: a generic JSON event, and a Discord embed (mentions disabled). */
object WebhookPayload {
    private const val DISCORD_DESCRIPTION_LIMIT = 4000
    private const val DISCORD_COLOR = 0x6A5ACD

    fun generic(event: WebhookEvent, data: Map<String, String>, timestamp: String, cover: String? = null): JsonObject =
        buildJsonObject {
            put("event", event.id)
            put("timestamp", timestamp)
            put("app", "Komikku")
            putJsonObject("data") { data.forEach { (key, value) -> put(key, value) } }
            cover?.let { put("cover_url", it) }
        }

    fun discord(event: WebhookEvent, data: Map<String, String>, timestamp: String, cover: String? = null): JsonObject =
        buildJsonObject {
            putJsonObject("allowed_mentions") { put("parse", buildJsonArray {}) }
            put(
                "embeds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("title", event.id.replace('_', ' ').replaceFirstChar { it.uppercase() })
                            put(
                                "description",
                                data.entries
                                    .joinToString("\n") { (key, value) -> "**${key.replace('_', ' ')}:** $value" }
                                    .take(DISCORD_DESCRIPTION_LIMIT),
                            )
                            put("timestamp", timestamp)
                            put("color", DISCORD_COLOR)
                            cover?.let { putJsonObject("thumbnail") { put("url", it) } }
                        },
                    )
                },
            )
        }
}
// KNS <--
