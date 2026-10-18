package tachiyomi.domain.manga.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

class MangaUpdate(val id: Long, block: MangaUpdate.() -> Unit) : PartialUpdate() {
    var source: Long? by field(null)
    var favorite: Boolean? by field(null)
    var lastUpdate: Instant? by field(null)
    var nextUpdate: Instant? by field(null)
    var fetchInterval: Int? by field(null)
    var dateAdded: Instant? by field(null)
    var viewerFlags: Long? by field(null)
    var chapterFlags: Long? by field(null)
    var coverLastModified: Instant? by field(null)
    var url: String? by field(null)
    var version: Long? by field(null)
    var notes: String? by field(null)
    // SY -->
    var filteredScanlators: List<String>? by field(null)
    // SY <--
    // KMK -->
    var scanlatorPriority: List<String>? by field(null)
    var blacklistedChapters: List<String>? by field(null)
    var scanlatorRangeRules: List<String>? by field(null)
    // KMK <--

    init {
        block()
    }
}

fun Manga.toMangaUpdate(): MangaUpdate {
    return MangaUpdate(id) {
        source = this@toMangaUpdate.source
        favorite = this@toMangaUpdate.favorite
        lastUpdate = this@toMangaUpdate.lastUpdate
        nextUpdate = this@toMangaUpdate.nextUpdate
        fetchInterval = this@toMangaUpdate.fetchInterval
        dateAdded = this@toMangaUpdate.dateAdded
        viewerFlags = this@toMangaUpdate.viewerFlags
        chapterFlags = this@toMangaUpdate.chapterFlags
        coverLastModified = this@toMangaUpdate.coverLastModified
        url = this@toMangaUpdate.url
        version = this@toMangaUpdate.version
        notes = this@toMangaUpdate.notes
        // KMK -->
        scanlatorPriority = this@toMangaUpdate.scanlatorPriority
        blacklistedChapters = this@toMangaUpdate.blacklistedChapters
        scanlatorRangeRules = this@toMangaUpdate.scanlatorRangeRules
        // KMK <--
    }
}
