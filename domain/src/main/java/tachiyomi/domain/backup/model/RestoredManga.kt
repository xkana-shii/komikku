package tachiyomi.domain.backup.model

import exh.metadata.metadata.base.FlatMetadata
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant

data class RestoredManga(
    val manga: Manga,
    val chapters: List<Chapter>,
    // KUN -->
    val categoryIds: List<Long>?,
    // KUN <--
    val history: List<RestoredHistory>,
    val tracks: List<Track>,
    val excludedScanlators: List<String>,
    // KMK/SY -->
    val mergedEntries: List<RestoredMergedEntry> = emptyList(),
    val mergeSettings: RestoredMergeSettings? = null,
    val flatMetadata: FlatMetadata? = null,
    // KMK/SY <--
    // KNS -->
    /** The fork-owned per-manga state of the payload; null when it carries none of it. */
    val forkState: RestoredForkState? = null,
    // KNS <--
    // KUN -->
    /**
     * Whether the payload carried the custom-info section at all. `true` with every override of
     * [manga] null means the user cleared every override and a winning restore may remove them
     * locally; `null`/`false` only allows merging the non-null overrides.
     */
    val customInfoIncluded: Boolean? = null,
    /**
     * Whether the payload carried the merged-reference list for this entry. `true` means the list is
     * complete and a winning restore may delete local references; `null`/`false` is a legacy or
     * partial payload and only allows adding/updating.
     */
    val mergedMangaReferencesIncluded: Boolean? = null,
    // KUN <--
)

// KNS -->
data class RestoredForkState(
    /** Whether the payload states the scanlator settings and blacklist (an empty list then means "none"). */
    val scanlatorSettingsPresent: Boolean,
    val blacklistedChapters: List<String>,
    val scanlatorPriority: List<String>,
    val scanlatorRangeRules: List<String>,
    /** Whether the payload states the Smart Merge override; [smartMergeOverride] null then means "global". */
    val smartMergeOverridePresent: Boolean,
    val smartMergeOverride: Boolean?,
)
// KNS <--

data class RestoredHistory(
    val chapterUrl: String,
    val readAt: Instant?,
    val readDuration: Long,
)

// SY -->
data class RestoredMergedEntry(
    val mangaUrl: String,
    val mangaSourceId: Long,
    val fetchChapterUpdates: Boolean,
    val downloadChapters: Boolean,
    val chapterPriority: Int,
)

data class RestoredMergeSettings(
    val chapterSortMode: Int,
    val infoMangaUrl: String,
    val infoMangaSourceId: Long,
)
// SY <--
