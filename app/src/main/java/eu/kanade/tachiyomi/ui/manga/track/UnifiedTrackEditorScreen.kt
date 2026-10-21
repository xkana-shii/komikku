package eu.kanade.tachiyomi.ui.manga.track

import android.content.Context
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.track.interactor.UnifiedTrackerEdits
import eu.kanade.domain.track.interactor.UpdateTracks
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Clock

// KNS -->
enum class UnifiedTrackField { STATUS, CHAPTERS, SCORE, START_DATE, END_DATE }

/**
 * Picks one value with the usual selectors and applies it to every tracker linked with the manga
 * ([UpdateTracks]); the selectors start from [track], the binding of the preferred tracker [trackerId].
 */
data class UnifiedTrackEditorScreen(
    private val mangaId: Long,
    private val track: Track,
    private val trackerId: Long,
    private val field: UnifiedTrackField,
) : Screen() {

    @Transient
    private val pastDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= Clock.System.now().toEpochMilliseconds()
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(mangaId = mangaId) }
        val tracker = remember { viewModel.tracker(trackerId) } ?: return
        val apply: (UpdateTracks.Change) -> Unit = { change ->
            viewModel.apply(change)
            navigator.pop()
        }
        when (field) {
            UnifiedTrackField.STATUS -> {
                var selection by remember { mutableLongStateOf(track.status) }
                TrackStatusSelector(
                    selection = selection,
                    onSelectionChange = { selection = it },
                    selections = remember { tracker.getStatusList().associateWith(tracker::getStatus) },
                    onConfirm = { apply(UpdateTracks.Change.Status(tracker.id, selection)) },
                    onDismissRequest = navigator::pop,
                )
            }
            UnifiedTrackField.CHAPTERS -> {
                var selection by remember { mutableIntStateOf(track.lastChapterRead.toInt()) }
                TrackChapterSelector(
                    selection = selection,
                    onSelectionChange = { selection = it },
                    range = 0..maxOf(track.totalChapters.toInt(), selection, 10_000),
                    onConfirm = { apply(UpdateTracks.Change.Progress(selection)) },
                    onDismissRequest = navigator::pop,
                )
            }
            UnifiedTrackField.SCORE -> {
                var selection by remember { mutableStateOf(tracker.displayScore(track)) }
                TrackScoreSelector(
                    selection = selection,
                    onSelectionChange = { selection = it },
                    selections = remember { tracker.getScoreList() },
                    onConfirm = { apply(UpdateTracks.Change.Score(tracker.id, selection)) },
                    onDismissRequest = navigator::pop,
                )
            }
            UnifiedTrackField.START_DATE, UnifiedTrackField.END_DATE -> {
                val start = field == UnifiedTrackField.START_DATE
                val current = if (start) track.startDate else track.finishDate
                TrackDateSelector(
                    title = stringResource(
                        if (start) MR.strings.track_started_reading_date else MR.strings.track_finished_reading_date,
                    ),
                    initialSelectedDateMillis = (current ?: Clock.System.now()).toEpochMilliseconds()
                        .convertEpochMillisZone(TimeZone.currentSystemDefault(), TimeZone.UTC),
                    selectableDates = pastDates,
                    onConfirm = { utc ->
                        val local = utc.convertEpochMillisZone(TimeZone.UTC, TimeZone.currentSystemDefault())
                        apply(UpdateTracks.Change.Date(start, local))
                    },
                    onRemove = { apply(UpdateTracks.Change.Date(start, 0)) }.takeIf { current != null },
                    onDismissRequest = navigator::pop,
                )
            }
        }
    }

    @AssistedInject
    class Model(
        @Assisted private val mangaId: Long,
        @AppCoroutineScope private val appScope: CoroutineScope,
        private val context: Context,
        private val trackerManager: TrackerManager,
        private val unifiedTrackerEdits: UnifiedTrackerEdits,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(mangaId: Long): Model
        }

        fun tracker(trackerId: Long) = trackerManager.get(trackerId)

        /** Runs on the app scope so closing the sheet does not cancel the remote writes. */
        fun apply(change: UpdateTracks.Change) {
            appScope.launch {
                val result = unifiedTrackerEdits.apply(mangaId, change)
                withContext(Dispatchers.Main) {
                    result.failures.forEach { (tracker, error) ->
                        context.toast(
                            context.stringResource(MR.strings.track_error, tracker.name, error.message ?: ""),
                        )
                    }
                    if (result.failures.isEmpty() && result.updatedTrackerIds.isNotEmpty()) {
                        context.toast(
                            context.stringResource(KMR.strings.tracker_updated_count, result.updatedTrackerIds.size),
                        )
                    }
                }
            }
        }
    }
}
// KNS <--
