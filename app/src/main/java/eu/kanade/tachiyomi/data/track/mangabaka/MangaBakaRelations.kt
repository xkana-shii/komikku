package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation

// KMK -->
internal fun MangaBakaItem.toRelatedEntry(relation: String, trackerId: Long): SequelPrequelEntry? {
    if (type.equals("novel", ignoreCase = true)) return null
    return SequelPrequelEntry(
        title = chooseBestTitle(),
        url = "https://mangabaka.org/$id",
        relation = SequelPrequelRelation.from(relation),
        trackerId = trackerId,
        remoteId = id,
        coverUrl = cover.x250.x1,
    )
}
// KMK <--
