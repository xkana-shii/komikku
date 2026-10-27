package tachiyomi.domain.manga.model

/**
 * SY-specific file
 */
data class MergeSettings(
    val mangaId: Long,
    val chapterSortMode: Int,
    val infoMangaId: Long?,
) {
    companion object {
        // KUN -->
        // Upstream numbers these NONE=0, NO_DEDUPE=1, PRIORITY=2, MOST=3, HIGHEST=4. This fork
        // dropped the "No dedupe" mode (Komikku #1194) and renumbered the stored values with it
        // (MergedMangaDedupeModeMigration), so its numbering has no gap. The views, the dedupe
        // query and every stored row use this numbering.
        const val CHAPTER_SORT_NONE = 0
        const val CHAPTER_SORT_PRIORITY = 1
        const val CHAPTER_SORT_MOST_CHAPTERS = 2
        const val CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER = 3
        // KUN <--

        /**
         * Modes where reordering the entries matters: NONE groups every chapter by entry priority,
         * PRIORITY keeps the highest-priority copy of each number.
         *
         * KUN: upstream also lists its `CHAPTER_SORT_NO_DEDUPE`; this fork has no such mode.
         */
        fun isReorderEnabled(chapterSortMode: Int): Boolean =
            chapterSortMode == CHAPTER_SORT_NONE ||
                chapterSortMode == CHAPTER_SORT_PRIORITY
    }
}
