package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.domain.manga.model.isNovelFormat

// KMK --> Normalize before relation lists enter caching or library matching.
internal fun MangaBakaItem.toRelatedEntry(relation: String, trackerId: Long): SequelPrequelEntry? {
    if (isNovelFormat(type)) return null
    return SequelPrequelEntry(
        title = chooseBestTitle(),
        url = "https://mangabaka.org/$id",
        relation = SequelPrequelRelation.from(relation),
        trackerId = trackerId,
        remoteId = id,
        coverUrl = cover.raw.url ?: cover.x350.x1,
        mediaFormat = type,
    )
}
// KMK <--
