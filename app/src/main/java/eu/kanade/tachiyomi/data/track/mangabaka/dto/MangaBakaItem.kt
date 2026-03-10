package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaBakaItemResult(
    val data: MangaBakaItem,
)

@Serializable
data class MangaBakaSearchResult(
    val data: List<MangaBakaItem>,
)

@Serializable
data class MangaBakaItem(
    val id: Long,
    val title: String,
    val cover: MangaBakaCover,
    val authors: List<String>?,
    val artists: List<String>?,
    val description: String?,
    val year: Int?,
    val status: String,
    val type: String,
    val rating: Double?,
    @SerialName("total_chapters")
    val totalChapters: String?,
    val state: String? = null,
    @SerialName("merged_with")
    val mergedWith: Long? = null,
)

@Serializable
data class MangaBakaCover(
    val raw: MangaBakaRawCover,
    val x150: MangaBakaScaledCover,
    val x250: MangaBakaScaledCover,
    val x350: MangaBakaScaledCover,
)

@Serializable
data class MangaBakaRawCover(
    val url: String,
)

@Serializable
data class MangaBakaScaledCover(
    val x1: String?,
    val x2: String?,
    val x3: String?,
)
