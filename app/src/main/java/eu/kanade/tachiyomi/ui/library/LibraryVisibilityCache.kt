package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.chapter.model.VisibleChapterStats
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.SmartMergeSettings
import tachiyomi.domain.manga.model.Manga

// KMK -->
/** Collection-local cache. Uses database revisions, never aggregate equality, to invalidate chapter statistics. */
internal class LibraryVisibilityCache {
    private val entries = mutableMapOf<Long, Entry>()

    suspend fun prepare(
        mangas: List<LibraryManga>,
        settings: SmartMergeSettings,
        load: suspend (List<Manga>, SmartMergeSettings) -> Map<Long, VisibleChapterStats>,
    ): List<LibraryManga> {
        entries.keys.retainAll(mangas.mapTo(HashSet()) { it.id })
        val missing = mangas.filter { manga ->
            val enabled = settings.enabledFor(manga.id)
            (enabled || manga.manga.blacklistedChapters.isNotEmpty()) && (entries[manga.id]?.enabled != enabled || entries[manga.id]?.revision != manga.visibilityRevision)
        }
        val stats = if (missing.isEmpty()) emptyMap() else load(missing.map(LibraryManga::manga), settings)
        missing.forEach { manga ->
            val count = checkNotNull(stats[manga.id])
            entries[manga.id] = Entry(
                settings.enabledFor(manga.id),
                manga.visibilityRevision,
                count,
            )
        }
        return mangas.map { manga ->
            if (settings.enabledFor(manga.id) || manga.manga.blacklistedChapters.isNotEmpty()) {
                val entry = entries.getValue(manga.id)
                entry.prepared?.takeIf { entry.input == manga } ?: manga.copy(
                    totalChapters = entry.stats.total,
                    readCount = entry.stats.read,
                    bookmarkCount = entry.stats.bookmarked,
                    bookmarkReadCount = entry.stats.bookmarkedRead,
                ).also {
                    entry.input = manga
                    entry.prepared = it
                }
            } else {
                entries.remove(manga.id)
                manga
            }
        }
    }

    private data class Entry(
        val enabled: Boolean,
        val revision: Long,
        val stats: VisibleChapterStats,
        var input: LibraryManga? = null,
        var prepared: LibraryManga? = null,
    )
}
// KMK <--
