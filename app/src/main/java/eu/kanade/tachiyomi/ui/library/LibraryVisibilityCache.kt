package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.chapter.model.VisibleChapterStats
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.SmartMergeSettings
import tachiyomi.domain.manga.model.Manga

// KMK -->
/** Collection-local cache. Never retains chapters or survives a database snapshot change. */
internal class LibraryVisibilityCache {
    private var snapshot: List<LibraryManga>? = null
    private val entries = mutableMapOf<Long, Entry>()

    suspend fun prepare(
        mangas: List<LibraryManga>,
        settings: SmartMergeSettings,
        load: suspend (List<Manga>, SmartMergeSettings) -> Map<Long, VisibleChapterStats>,
    ): List<LibraryManga> {
        // Aggregate counts and manga versions cannot identify every chapter change (for
        // example a scanlator edit with identical totals, or a merged source change).
        // A new database emission must invalidate statistics even if row values are equal.
        if (snapshot !== mangas) {
            entries.clear()
            snapshot = mangas
        }
        val missing = mangas.filter { manga ->
            val enabled = settings.enabledFor(manga.id)
            (enabled || manga.manga.blacklistedChapters.isNotEmpty()) && entries[manga.id]?.enabled != enabled
        }
        val stats = if (missing.isEmpty()) emptyMap() else load(missing.map(LibraryManga::manga), settings)
        missing.forEach { manga ->
            val count = checkNotNull(stats[manga.id])
            entries[manga.id] = Entry(
                settings.enabledFor(manga.id),
                manga.copy(
                    totalChapters = count.total,
                    readCount = count.read,
                    bookmarkCount = count.bookmarked,
                    bookmarkReadCount = count.bookmarkedRead,
                ),
            )
        }
        return mangas.map { manga ->
            if (settings.enabledFor(manga.id) || manga.manga.blacklistedChapters.isNotEmpty()) {
                entries.getValue(manga.id).manga
            } else {
                entries.remove(manga.id)
                manga
            }
        }
    }

    private data class Entry(val enabled: Boolean, val manga: LibraryManga)
}
// KMK <--
