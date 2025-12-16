package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MBMangaMetadata(
    val data: MBMangaMetadataData,
)

@Serializable
data class MBMangaMetadataData(
    @SerialName("Media")
    val media: MBMangaMetadataMedia,
)

@Serializable
data class MBMangaMetadataMedia(
    val id: Long,
    val title: MBItemTitle,
    val coverImage: MBItemCover,
    val description: String?,
    val staff: MBStaff,
)
