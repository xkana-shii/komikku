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
    // KNS --> optional: metadata autofill must not fail on a series without a status or type
    val status: String? = null,
    val type: String? = null,
    // KNS <--
    val rating: Double?,
    val titles: List<MangaBakaItemTitle>?,
    // KNS -->
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    /** `active`, `merged` or `deleted`; a merged series points at its replacement with [mergedWith]. */
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
    // KNS --> the original image, which metadata autofill prefers
    val raw: MangaBakaRawCover? = null,
    // KNS <--
    val x250: MangaBakaScaledCover,
)

// KNS -->
@Serializable
data class MangaBakaRawCover(
    val url: String? = null,
)
// KNS <--

@Serializable
data class MangaBakaScaledCover(
    val x1: String?,
)

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
