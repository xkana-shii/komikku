
package eu.kanade.tachiyomi.data.track.mangabaka

import java.net.URI

sealed interface MangaBakaUrlResolution {

    data object NotMangaBakaUrl : MangaBakaUrlResolution

    data object MissingExternalSource : MangaBakaUrlResolution

    data class ExternalSourceId(
        val id: String,
    ) : MangaBakaUrlResolution
}

/**
 * Extracts a MangaBaka series ID from supported page URLs.
 *
 * Examples:
 * https://mangabaka.org/manhwa/583843/The-Milky-Way
 * https://www.mangabaka.org/583843
 */
fun parseMangaBakaSeriesId(
    value: String,
): Long? {
    val uri = try {
        URI(value.trim())
    } catch (_: Exception) {
        return null
    }

    val host = uri.host
        ?.lowercase()
        ?.removePrefix("www.")
        ?: return null

    if (host != "mangabaka.org") {
        return null
    }

    if (uri.scheme !in listOf("https", "http")) {
        return null
    }

    val segments = uri.path
        .orEmpty()
        .trim('/')
        .split('/')
        .filter { it.isNotEmpty() }

    if (segments.isEmpty()) {
        return null
    }

    val id = when {
        segments.first().toLongOrNull() != null -> {
            segments.first().toLongOrNull()
        }

        segments.size >= 2 &&
            segments.first().lowercase() in setOf(
                "manga",
                "manhwa",
                "manhua",
                "novel",
                "series",
            ) -> {
            segments[1].toLongOrNull()
        }

        else -> null
    }

    return id?.takeIf { it > 0L }
}

/**
 * Converts a MangaBaka page URL into the external series ID
 * for a particular tracker.
 *
 * The supplied lookup must return the tracker-specific ID,
 * not the MangaBaka internal ID.
 */
class MangaBakaUrlResolver(
    private val lookupExternalId: suspend (
        seriesId: Long,
        trackerId: Long,
    ) -> String?,
) {

    suspend fun resolve(
        query: String,
        trackerId: Long,
    ): MangaBakaUrlResolution {
        val seriesId = parseMangaBakaSeriesId(query)
            ?: return MangaBakaUrlResolution.NotMangaBakaUrl

        val externalId = lookupExternalId(
            seriesId,
            trackerId,
        )?.trim()

        return if (externalId.isNullOrBlank()) {
            MangaBakaUrlResolution.MissingExternalSource
        } else {
            MangaBakaUrlResolution.ExternalSourceId(
                externalId,
            )
        }
    }
}
