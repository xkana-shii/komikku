package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import kotlinx.serialization.Serializable

@Serializable
data class MUSeries(
    val id: Long? = null,
    val title: String? = null,
    val authors: List<MUAuthor>? = null,
) {
    @Serializable
    data class MUAuthor(
        val name: String,
        val authorId: Long? = null,
        val url: String? = null,
        val type: String
    )
}
