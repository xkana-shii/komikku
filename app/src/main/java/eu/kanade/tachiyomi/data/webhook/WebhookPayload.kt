package eu.kanade.tachiyomi.data.webhook

import eu.kanade.domain.connections.service.WebhookEvent
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// KMK --> Pure payload builders, shared by normal delivery and Send test.
object WebhookPayload {
    fun generic(event: WebhookEvent, data: Map<String, String>, timestamp: String, cover: String? = null) = buildJsonObject {
        put("event", event.id)
        put("timestamp", timestamp)
        put("app", "Komikku")
        putJsonObject("data") { data.forEach { (key, value) -> put(key, value) } }
        cover?.let { put("cover_url", it) }
    }

    fun discord(event: WebhookEvent, data: Map<String, String>, timestamp: String, cover: String? = null) = buildJsonObject {
        putJsonObject("allowed_mentions") { put("parse", buildJsonArray {}) }
        put(
            "embeds",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("title", event.id.replace('_', ' ').replaceFirstChar { it.uppercase() })
                        put("description", data.entries.joinToString("\n") { (key, value) -> "**${key.replace('_', ' ')}:** $value" }.take(4000))
                        put("timestamp", timestamp)
                        put("color", 0x6A5ACD)
                        cover?.let { putJsonObject("thumbnail") { put("url", it) } }
                    },
                )
            },
        )
    }
}
// KMK <--
