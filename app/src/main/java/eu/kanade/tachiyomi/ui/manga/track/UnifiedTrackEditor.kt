package eu.kanade.tachiyomi.ui.manga.track

import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.domain.track.interactor.UpdateTracks
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneOffset

// KMK --> Reuse the normal editors; the screen model handles every unified write.
internal enum class UnifiedTrackField { PROGRESS, SCORE, STATUS, START_DATE, END_DATE }

@Composable
internal fun UnifiedTrackEditor(item: TrackItem, field: UnifiedTrackField, onApply: (UpdateTracks.Change) -> Unit, onDismiss: () -> Unit) {
    val track = item.track ?: return
    val service = item.tracker
    when (field) {
        UnifiedTrackField.PROGRESS -> {
            var selection by remember { mutableStateOf(track.lastChapterRead.toInt()) }
            TrackChapterSelector(selection, { selection = it }, 0..maxOf(track.totalChapters.toInt(), selection, 10000), { onApply(UpdateTracks.Change.Progress(selection)) }, onDismiss)
        }
        UnifiedTrackField.SCORE -> {
            var selection by remember { mutableStateOf(service.displayScore(track)) }
            TrackScoreSelector(selection, { selection = it }, service.getScoreList(), { onApply(UpdateTracks.Change.Score(service.id, selection)) }, onDismiss)
        }
        UnifiedTrackField.STATUS -> {
            var selection by remember { mutableStateOf(track.status) }
            TrackStatusSelector(selection, { selection = it }, service.getStatusList().associateWith(service::getStatus), { onApply(UpdateTracks.Change.Status(service.id, selection)) }, onDismiss)
        }
        UnifiedTrackField.START_DATE, UnifiedTrackField.END_DATE -> {
            val start = field == UnifiedTrackField.START_DATE
            val value = if (start) track.startDate else track.finishDate
            val dates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= Instant.now().toEpochMilli()
                }
            }
            TrackDateSelector(
                title = stringResource(if (start) MR.strings.track_started_reading_date else MR.strings.track_finished_reading_date),
                initialSelectedDateMillis = (value.takeIf { it > 0 } ?: Instant.now().toEpochMilli()).convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC),
                selectableDates = dates,
                onConfirm = { onApply(UpdateTracks.Change.Date(start, it.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault()))) },
                onRemove = { onApply(UpdateTracks.Change.Date(start, 0)) },
                onDismissRequest = onDismiss,
            )
        }
    }
}
// KMK <--
