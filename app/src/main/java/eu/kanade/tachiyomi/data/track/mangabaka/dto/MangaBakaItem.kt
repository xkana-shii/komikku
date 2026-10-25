package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val TITLE_PRIORITIES = listOf("en", "ja-Latn", "ja", "ko-Latn", "ko", "zh-Latn", "zh")

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
    val cover: MangaBakaCover,
    val authors: List<String>?,
    val artists: List<String>?,
    val description: String?,
    val published: MangaBakaPublishData,
    // KNS --> Metadata autofill accepts incomplete MangaBaka responses.
    val status: String = "",
    val type: String = "",
    // KNS <--
    val rating: Double?,
    val titles: List<MangaBakaItemTitle>?,
    // KNS -->
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val state: String? = null,
    @SerialName("merged_with")
    val mergedWith: Long? = null,
    @SerialName("relationships_v2")
    val relationships: List<MangaBakaRelationship>? = null,
    // KNS <--
) {
    fun chooseBestTitle(): String {
        // based on https://mangabaka.org/pages/announcements/15-titles-v2#finding-the-title-you-want
        // extended with zh-Latn and zh
        val bestTitlePerLanguage = TITLE_PRIORITIES.associateWith { lang ->
            titles?.filter { it.language == lang }
                ?.minByOrNull {
                    when {
                        it.isPrimary -> 0
                        "official" in it.traits -> 1
                        "native" in it.traits -> 2
                        else -> 3
                    }
                }
        }

        return TITLE_PRIORITIES
            .firstNotNullOfOrNull { bestTitlePerLanguage[it]?.title }
            ?: titles?.firstOrNull()?.title
            ?: "ID: $id - Could not find name! (report on the MangaBaka Discord)"
    }
}

// KNS -->
@Serializable
data class MangaBakaRelationship(
    @SerialName("to_series_id")
    val seriesId: Long,
    @SerialName("relation_type")
    val relationType: String,
)
// KNS <--

@Serializable
data class MangaBakaCover(
    // SY -->
    // KUN -->: defaulted, because this DTO also decodes search results and a response without the
    // unscaled cover would otherwise fail the whole search.
    val raw: MangaBakaRawCover = MangaBakaRawCover(null),
    // KUN <--
    // SY <--
    val x250: MangaBakaScaledCover,
)

@Serializable
data class MangaBakaScaledCover(
    val x1: String?,
)

// SY -->
@Serializable
data class MangaBakaRawCover(
    val url: String?,
)
// SY <--

@Serializable
data class MangaBakaPublishData(
    @SerialName("start_date")
    val startDate: String?,
)

@Serializable
data class MangaBakaItemTitle(
    val language: String,
    val traits: List<String>,
    val title: String,
    @SerialName("is_primary")
    val isPrimary: Boolean,
)
