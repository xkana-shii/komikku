package eu.kanade.presentation.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.test.DummyTracker
import tachiyomi.domain.track.model.Track
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val aTrack = Track(
        id = 1L,
        mangaId = 2L,
        trackerId = 2L,
        remoteId = 4L,
        libraryId = null,
        title = "Manage Name On Tracker Site",
        lastChapterRead = 2.0,
        totalChapters = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
    private val privateTrack = aTrack.copy(private = true)
    private val trackItemWithoutTrack = TrackItem(
        track = null,
        tracker = DummyTracker(
            id = 1L,
            name = "Example Tracker",
        ),
    )
    private val trackItemWithTrack = TrackItem(
        track = aTrack,
        tracker = DummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )
    private val trackItemWithPrivateTrack = TrackItem(
        track = privateTrack,
        tracker = DummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )

    private val trackersWithAndWithoutTrack = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            seriesTitle = SERIES_TITLE,
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val noTrackers = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(),
            seriesTitle = SERIES_TITLE,
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val trackerWithPrivateTracking = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            seriesTitle = SERIES_TITLE,
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    // KMK -->
    private val multipleBoundTrackers = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithTrack,
                TrackItem(
                    track = aTrack.copy(id = 2L, trackerId = 4L, lastChapterRead = 5.0, score = 8.0),
                    tracker = DummyTracker(id = 4L, name = "Example Tracker 3", supportsReadingDates = true),
                ),
                trackItemWithoutTrack,
            ),
            seriesTitle = SERIES_TITLE,
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }
    // KMK <--

    override val values: Sequence<@Composable () -> Unit>
        get() = sequenceOf(
            trackersWithAndWithoutTrack,
            noTrackers,
            trackerWithPrivateTracking,
            multipleBoundTrackers,
        )

    companion object {
        private const val SERIES_TITLE = "Ascendance of a Bookworm: I'll Do Anything to Become a Librarian!"
    }
}
