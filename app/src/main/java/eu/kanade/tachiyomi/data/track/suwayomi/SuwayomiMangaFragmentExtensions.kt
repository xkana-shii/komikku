package eu.kanade.tachiyomi.data.track.suwayomi

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import mihon.graphql.suwayomi.fragment.MangaFragment

internal fun MangaFragment.toTrackSearch(trackId: Long, baseUrl: String): TrackSearch {
    val manga = this
    return TrackSearch.create(trackId).apply {
        remote_id = manga.id.toLong()
        title = manga.title
        // KUN -->
        // thumbnailUrl is nullable; don't build a ".../null" URL when the source has no thumbnail
        cover_url = manga.thumbnailUrl?.let { "$baseUrl/$it" }.orEmpty()
        // KUN <--
        summary = manga.description.orEmpty()
        tracking_url = "$baseUrl/manga/${manga.id}"
        total_chapters = manga.chapters.totalCount.toLong()
        publishing_status = manga.status.name
        last_chapter_read = manga.latestReadChapter?.chapterNumber ?: 0.0
        status = when (manga.unreadCount) {
            manga.chapters.totalCount -> Suwayomi.UNREAD
            0 -> Suwayomi.COMPLETED
            else -> Suwayomi.READING
        }
    }
}
