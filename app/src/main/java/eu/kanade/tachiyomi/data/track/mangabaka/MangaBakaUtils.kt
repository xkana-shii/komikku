package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.source.model.SManga

internal fun Track.toApiStatus() = when (status) {
    MangaBaka.CONSIDERING -> "considering"
    MangaBaka.COMPLETED -> "completed"
    MangaBaka.DROPPED -> "dropped"
    MangaBaka.PAUSED -> "paused"
    MangaBaka.PLAN_TO_READ -> "plan_to_read"
    MangaBaka.READING -> "reading"
    MangaBaka.REREADING -> "rereading"
    else -> throw NotImplementedError("Unknown status: $status")
}

// KNS -->
/**
 * Series metadata for autofill (adapted from the previous personal fork). Genres (`slice_of_life`)
 * become readable tags (`Slice of life`) followed by the series tags; the publication status maps to
 * an `SManga` status. Anything missing, empty or unknown (`upcoming`, `unknown`) stays null, so
 * autofill keeps what the editor has.
 */
internal fun MangaBakaItem.toAutofillMetadata(description: String?) = TrackMangaMetadata(
    remoteId = id,
    title = chooseBestTitle(),
    // The original image (as the previous personal fork used); the 250px one only when there is none.
    thumbnailUrl = cover.raw?.url?.ifEmpty { null } ?: cover.x250.x1?.ifEmpty { null },
    description = description?.ifEmpty { null },
    authors = authors?.joinToString(", ")?.ifEmpty { null },
    artists = artists?.joinToString(", ")?.ifEmpty { null },
    tags = autofillTags(
        genres.orEmpty().map { it.replace('_', ' ').trim().replaceFirstChar(Char::titlecase) } + tags.orEmpty(),
    ),
    status = when (status?.trim()?.lowercase()) {
        "releasing", "ongoing" -> SManga.ONGOING.toLong()
        "completed" -> SManga.COMPLETED.toLong()
        "cancelled" -> SManga.CANCELLED.toLong()
        "hiatus" -> SManga.ON_HIATUS.toLong()
        else -> null
    },
)
// KNS <--
