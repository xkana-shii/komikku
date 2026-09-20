package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toApiStatus() = when (status) {
    MangaBaka.CONSIDERING -> "considering"
    MangaBaka.COMPLETED -> "completed"
    MangaBaka.DROPPED -> "dropped"
    MangaBaka.PAUSED -> "paused"
    MangaBaka.PLAN_TO_READ -> "plan_to_read"
    MangaBaka.READING -> "reading"
    MangaBaka.REREADING -> "rereading"
    else -> throw NotImplementedError("Unknown status: $status")
}

// KMK --> Metadata missing from the provider stays null, so autofill keeps existing edits.
internal fun eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem.toAutofillMetadata(description: String?) =
    eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata(
        remoteId = id,
        title = chooseBestTitle(),
        thumbnailUrl = cover.raw.url,
        description = description?.ifEmpty { null },
        authors = authors?.joinToString(", ")?.ifEmpty { null },
        artists = artists?.joinToString(", ")?.ifEmpty { null },
        tags = (genres.orEmpty().map { it.replace('_', ' ').replaceFirstChar(Char::titlecase) } + tags.orEmpty())
            .map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase).ifEmpty { null },
        status = when (status?.trim()?.lowercase()) {
            "releasing", "ongoing" -> eu.kanade.tachiyomi.source.model.SManga.ONGOING.toLong()
            "completed" -> eu.kanade.tachiyomi.source.model.SManga.COMPLETED.toLong()
            "cancelled" -> eu.kanade.tachiyomi.source.model.SManga.CANCELLED.toLong()
            "hiatus" -> eu.kanade.tachiyomi.source.model.SManga.ON_HIATUS.toLong()
            else -> null
        },
    )
// KMK <--
