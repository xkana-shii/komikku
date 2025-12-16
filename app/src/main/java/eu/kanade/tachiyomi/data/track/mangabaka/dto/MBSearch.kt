package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MBSearchResult(
    val data: MBSearchPage,
)

@Serializable
data class MBSearchPage(
    @SerialName("Page")
    val page: MBSearchMedia,
)

@Serializable
data class MBSearchMedia(
    val media: List<MBSearchItem>,
)

@Serializable
data class MBSearchItem(
    val id: Long,
    val title: MBItemTitle,
    val coverImage: MBItemCover,
    val description: String?,
    val format: String?,
    val status: String?,
    val startDate: MBFuzzyDate,
    val chapters: Long?,
    val averageScore: Int?,
    val staff: MBStaff,
) {
    fun toMBManga(): MBManga = MBManga(
        remoteId = id,
        title = title.userPreferred,
        imageUrl = coverImage.large,
        description = description,
        format = format?.replace("_", "-") ?: "",
        publishingStatus = status ?: "",
        startDateFuzzy = startDate.toEpochMilli(),
        totalChapters = chapters ?: 0,
        averageScore = averageScore ?: -1,
        staff = staff,
    )
}

@Serializable
data class MBItemTitle(val userPreferred: String)

@Serializable
data class MBItemCover(val large: String)

@Serializable
data class MBStaff(val edges: List<MBEdge>)

@Serializable
data class MBEdge(val role: String, val node: MBStaffNode)

@Serializable
data class MBStaffNode(val name: MBStaffName)

@Serializable
data class MBStaffName(val userPreferred: String?, val native: String?, val full: String?) {
    operator fun invoke(): String? = userPreferred ?: full ?: native
}
