package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable(with = FlexibleIdSerializer::class)
data class FlexibleId(val value: String)

object FlexibleIdSerializer : KSerializer<FlexibleId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: FlexibleId) {
        encoder.encodeString(value.value)
    }
    override fun deserialize(decoder: Decoder): FlexibleId {
        val primitive = decoder.decodeSerializableValue(JsonPrimitive.serializer())
        return FlexibleId(primitive.content)
    }
}

@Serializable
data class MBRecord(
    val id: Long,
    val state: String? = null,
    val merged_with: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val native_title: String? = null,
    val romanized_title: String? = null,
    val secondary_titles: MBSecondaryTitles? = null,
    val cover: MBCover? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val description: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val is_licensed: Boolean? = null,
    val has_anime: Boolean? = null,
    val anime: MBAnimeInfo? = null,
    val content_rating: String? = null,
    val type: String? = null,
    val rating: Double? = null,
    val final_volume: String? = null,
    val final_chapter: String? = null,
    val total_chapters: String? = null,
    val links: List<String>? = null,
    val publishers: List<MBPublisher>? = null,
    val genres_v2: List<MBGenreV2>? = null,
    val genres: List<String>? = null,
    val tags_v2: List<MBTagV2>? = null,
    val tags: List<String>? = null,
    val last_updated_at: String? = null,
    val relationships: MBRelationships? = null,
    val source: MBSource? = null,
    val alt_titles: List<String>? = null,
    val title_en: String? = null,
    val title_ja: String? = null,
    val last_chapter: String? = null,
    val last_volume: String? = null,
    @SerialName("total_volumes") val total_volumes: String? = null,
    val average_rating: Double? = null,
    val bayesian_rating: Double? = null,
    val popularity: Int? = null,
    @SerialName("user_rating") val user_rating: Double? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class MBSecondaryTitles(
    val en: List<MBSecondaryTitleItem>? = null,
    val ja: List<MBSecondaryTitleItem>? = null,
)

@Serializable
data class MBSecondaryTitleItem(
    val type: String? = null,
    val title: String? = null,
)

@Serializable
data class MBCover(
    val raw: MBImage? = null,
    val x150: MBCoverSizes? = null,
    val x250: MBCoverSizes? = null,
    val x350: MBCoverSizes? = null,
)

@Serializable
data class MBImage(
    val url: String? = null,
    val size: Long? = null,
    val height: Int? = null,
    val width: Int? = null,
    val blurhash: String? = null,
    val thumbhash: String? = null,
    val format: String? = null,
)

@Serializable
data class MBCoverSizes(
    val x1: String? = null,
    val x2: String? = null,
    val x3: String? = null,
)

@Serializable
data class MBAnimeInfo(
    val start: String? = null,
    val end: String? = null,
    val episodes: Int? = null,
    val type: String? = null,
    val status: String? = null,
    val url: String? = null,
)

@Serializable
data class MBPublisher(
    val name: String? = null,
    val type: String? = null,
    val note: String? = null,
    val region: String? = null,
)

@Serializable
data class MBGenreV2(
    val id: Int? = null,
    val parent_id: Int? = null,
    val name: String? = null,
    val name_path: String? = null,
    val description: String? = null,
    val is_spoiler: Boolean? = null,
    val content_rating: String? = null,
    val series_count: Int? = null,
    val level: Int? = null,
)

@Serializable
data class MBTagV2(
    val id: Int? = null,
    val parent_id: Int? = null,
    val name: String? = null,
    val name_path: String? = null,
    val description: String? = null,
    val is_spoiler: Boolean? = null,
    val content_rating: String? = null,
    val series_count: Int? = null,
    val level: Int? = null,
)

@Serializable
data class MBRelationships(
    val adaptation: List<Long>? = null,
    val side_story: List<Long>? = null,
    val spin_off: List<Long>? = null,
    val prequel: List<Long>? = null,
    val main_story: List<Long>? = null,
    val alternative: List<Long>? = null,
    val other: List<Long>? = null,
    val related: List<Long>? = null,
)

@Serializable
data class MBSource(
    val anilist: MBSourceItem? = null,
    val anime_planet: MBSourceItem? = null,
    val anime_news_network: MBSourceItem? = null,
    val kitsu: MBSourceItem? = null,
    val manga_updates: MBSourceItem? = null,
    val mangadex: MBSourceItem? = null,
    val my_anime_list: MBSourceItem? = null,
    val shikimori: MBSourceItem? = null,
    val official_site: MBSourceItem? = null,
)

@Serializable
data class MBSourceItem(
    val id: FlexibleId? = null,
    val rating: Double? = null,
    @SerialName("rating_normalized") val ratingNormalized: Double? = null,
    val url: String? = null,
)

fun MBRecord.toTrackSearch(trackerId: Long): TrackSearch {
    val URL_BASE = "https://mangabaka.org"

    return TrackSearch.create(trackerId).apply {
        remote_id = this@toTrackSearch.id
        title = this@toTrackSearch.title?.htmlDecode() ?: ""
        total_chapters = 0
        cover_url = this@toTrackSearch.cover?.raw?.url ?: ""
        summary = this@toTrackSearch.description?.htmlDecode() ?: ""
        tracking_url = "$URL_BASE/${this@toTrackSearch.id}"
        publishing_status = this@toTrackSearch.status ?: ""
        publishing_type = this@toTrackSearch.type?.toString() ?: ""
        start_date = this@toTrackSearch.year?.toString() ?: ""
        score = this@toTrackSearch.rating?.toInt()?.toDouble() ?: 0.0
        authors = this@toTrackSearch.authors ?: emptyList()
        artists = this@toTrackSearch.artists ?: emptyList()
    }
}
