package eu.kanade.presentation.history.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.history.model.HistoryWithRelations
import kotlin.time.Instant

internal class HistoryWithRelationsProvider : PreviewParameterProvider<HistoryWithRelations> {

    private val simple = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = 10.2,
        // KMK -->
        read = true,
        lastPageRead = 5,
        totalCountCalculated = 5L,
        readCountCalculated = 3L,
        // KMK <--
        readAt = Instant.fromEpochMilliseconds(1697247357L),
        readDuration = 123L,
        coverData = tachiyomi.domain.manga.model.MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = Instant.fromEpochMilliseconds(5L),
        ),
    )

    private val historyWithoutReadAt = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = 10.2,
        // KMK -->
        read = false,
        lastPageRead = 5,
        totalCountCalculated = 5L,
        readCountCalculated = 3L,
        // KMK <--
        readAt = null,
        readDuration = 123L,
        coverData = tachiyomi.domain.manga.model.MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = Instant.fromEpochMilliseconds(5L),
        ),
    )

    private val historyWithNegativeChapterNumber = HistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        mangaId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        chapterNumber = -2.0,
        // KMK -->
        read = true,
        lastPageRead = 5,
        totalCountCalculated = 5L,
        readCountCalculated = 3L,
        // KMK <--
        readAt = Instant.fromEpochMilliseconds(1697247357L),
        readDuration = 123L,
        coverData = tachiyomi.domain.manga.model.MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = Instant.fromEpochMilliseconds(5L),
        ),
    )

    override val values: Sequence<HistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
