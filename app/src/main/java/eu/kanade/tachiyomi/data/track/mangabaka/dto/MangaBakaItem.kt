package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val TITLE_PRIORITIES = listOf(
    "en",
    "ja-Latn",
    "ja",
    "ko-Latn",
    "ko",
    "zh-Latn",
    "zh",
)

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
    val status: String,
    val type: String,
    val rating: Double?,
    val titles: List<MangaBakaItemTitle>?,

    // KMK --> Relations and merged IDs.
    @SerialName("merged_with")
    val mergedWith: Long? = null,

    @SerialName("relationships_v2")
    val relationships: List<MangaBakaRelationship>? = null,

    // Preserve optional external tracker bindings.
    val source: MangaBakaExternalSources? = null,
    // KMK <--
) {

    fun chooseBestTitle(): String {
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
            .firstNotNullOfOrNull {
                bestTitlePerLanguage[it]?.title
            }
            ?: titles?.firstOrNull()?.title
            ?: "ID: $id - Could not find name! (report on the MangaBaka Discord)"
    }
}

// KMK -->
@Serializable
data class MangaBakaExternalSources(
    @SerialName("manga_updates")
    val mangaUpdates: MangaBakaExternalStringId? = null,

    @SerialName("my_anime_list")
    val myAnimeList: MangaBakaExternalLongId? = null,
)

@Serializable
data class MangaBakaExternalStringId(
    val id: String,
)

@Serializable
data class MangaBakaExternalLongId(
    val id: Long,
)

@Serializable
data class MangaBakaRelationship(
    @SerialName("to_series_id")
    val seriesId: Long,

    @SerialName("relation_type")
    val relationType: String,
)
// KMK <--

@Serializable
data class MangaBakaCover(
    val x250: MangaBakaScaledCover,
)

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
