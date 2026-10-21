package tachiyomi.domain.chapter.model

import mihon.domain.common.PartialUpdate
import kotlin.time.Instant

class ChapterUpdate(val id: Long, block: ChapterUpdate.() -> Unit) : PartialUpdate() {
    var read: Boolean? by field(null)
    var bookmark: Boolean? by field(null)
    var lastPageRead: Int? by field(null)
    var dateFetch: Instant? by field(null)
    var version: Long? by field(null)

    // KNS -->
    var bookmarkColor: Int? by field(null)
    // KNS <--

    init {
        block()
    }
}

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(id) {
        read = this@toChapterUpdate.read
        bookmark = this@toChapterUpdate.bookmark
        lastPageRead = this@toChapterUpdate.lastPageRead
        dateFetch = this@toChapterUpdate.dateFetch
        version = this@toChapterUpdate.version
        // KNS -->
        bookmarkColor = this@toChapterUpdate.bookmarkColor
        // KNS <--
    }
}
