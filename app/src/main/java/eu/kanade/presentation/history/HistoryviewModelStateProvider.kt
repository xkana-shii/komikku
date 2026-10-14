package eu.kanade.presentation.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.HistoryViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class HistoryviewModelStateProvider : PreviewParameterProvider<HistoryViewModel.State> {

    private val multiPage = HistoryViewModel.State(
        searchQuery = null,
        list =
        // KMK -->
        listOf(HistoryWithRelationExamples.headerToday)
            .asSequence()
            .plus(HistoryWithRelationExamples.items().take(3))
            .plus(HistoryWithRelationExamples.header { it.minus(1.days) })
            .plus(HistoryWithRelationExamples.items().take(1))
            .plus(HistoryWithRelationExamples.header { it.minus(2.days) })
            .plus(HistoryWithRelationExamples.items().take(7))
            // KMK <--
            .toList(),
        dialog = null,
    )

    private val shortRecent = HistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            // KMK -->
            HistoryWithRelationExamples.headerToday,
            HistoryWithRelationExamples.items().first(),
            // KMK <--
        ),
        dialog = null,
    )

    private val shortFuture = HistoryViewModel.State(
        searchQuery = null,
        list = listOf(
            // KMK -->
            HistoryWithRelationExamples.headerTomorrow,
            HistoryWithRelationExamples.items().first(),
            // KMK <--
        ),
        dialog = null,
    )

    private val empty = HistoryViewModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = HistoryViewModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = HistoryViewModel.State(
        searchQuery = null,
        // KMK -->
        isLoading = true,
        // KMK <--
        dialog = null,
    )

    override val values: Sequence<HistoryViewModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    // KMK -->
    private object HistoryWithRelationExamples {
        val headerToday = randItem()
        val headerTomorrow = randItem(Clock.System.now().plus(1.days))

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            randItem(instantBuilder(Clock.System.now()))
        // KMK <--

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(/* SY --> */ogTitle = /* SY <-- */ "Example Title $count") })
                count += 1
            }
        }

        fun randItem(
            // KMK -->
            readAt: Instant = Clock.System.now(),
            // KMK <--
            historyBuilder: (HistoryWithRelations) -> HistoryWithRelations = { it },
        ) =
            historyBuilder(
                HistoryWithRelations(
                    id = Random.nextLong(),
                    chapterId = Random.nextLong(),
                    mangaId = Random.nextLong(),
                    // SY -->
                    ogTitle = "Test Title",
                    // SY <--
                    chapterNumber = Random.nextDouble(),
                    // KMK -->
                    read = Random.nextBoolean(),
                    lastPageRead = Random.nextInt(1, 10),
                    totalCountCalculated = Random.nextLong(1, 100),
                    readCountCalculated = 1,
                    readAt = readAt,
                    // KMK <--
                    readDuration = Random.nextLong(),
                    coverData = MangaCover(
                        mangaId = Random.nextLong(),
                        sourceId = Random.nextLong(),
                        isMangaFavorite = Random.nextBoolean(),
                        ogUrl = "https://example.com/cover.png",
                        lastModified = Instant.fromEpochMilliseconds(Random.nextLong()),
                    ),
                ),
            )
    }
}
