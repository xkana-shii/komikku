package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MBAddMangaResult(
    val data: MBAddMangaData,
)

@Serializable
data class MBAddMangaData(
    @SerialName("SaveMediaListEntry")
    val entry: MBAddMangaEntry,
)

@Serializable
data class MBAddMangaEntry(
    val id: Long,
)
