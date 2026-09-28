package eu.kanade.tachiyomi.data.track.mangabaka

import java.net.URI

internal sealed interface MangaBakaUrlResolution {
    data object NotMangaBakaUrl : MangaBakaUrlResolution

    data object MissingExternalSource : MangaBakaUrlResolution

    data class ExternalSourceId(val value: String) : MangaBakaUrlResolution
}

internal class MangaBakaUrlResolver(
    private val externalSourceId: suspend (seriesId: Long, trackerId: Long) -> String?,
) {
    constructor(mangaBaka: MangaBaka) : this(mangaBaka::getExternalSourceId)

    suspend fun resolve(query: String, trackerId: Long): MangaBakaUrlResolution {
        val seriesId = parseMangaBakaSeriesId(query) ?: return MangaBakaUrlResolution.NotMangaBakaUrl
        return externalSourceId(seriesId, trackerId)
            ?.takeIf(String::isNotBlank)
            ?.let(MangaBakaUrlResolution::ExternalSourceId)
            ?: MangaBakaUrlResolution.MissingExternalSource
    }
}

internal fun parseMangaBakaSeriesId(query: String): Long? = runCatching {
    URI(query.trim()).takeIf { uri ->
        uri.scheme in setOf("http", "https") && uri.host?.lowercase() in setOf("mangabaka.org", "www.mangabaka.org")
    }?.path?.split('/')?.firstNotNullOfOrNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }
}.getOrNull()
