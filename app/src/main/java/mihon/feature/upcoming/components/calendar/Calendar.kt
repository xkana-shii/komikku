package mihon.feature.upcoming.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import mihon.core.designsystem.utils.isExpandedWidthWindow
import mihon.core.designsystem.utils.isMediumWidthWindow
import tachiyomi.presentation.core.components.material.padding
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock

private val FontSize = 16.sp
private const val DAYS_OF_WEEK = 7

@Composable
fun Calendar(
    selectedYearMonth: YearMonth,
    events: Map<LocalDate, Int>,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickDay: (day: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalenderHeader(
            yearMonth = selectedYearMonth,
            onPreviousClick = { setSelectedYearMonth(selectedYearMonth.minusMonth()) },
            onNextClick = { setSelectedYearMonth(selectedYearMonth.plusMonth()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.small)
                .padding(start = MaterialTheme.padding.medium),
        )
        CalendarGrid(
            selectedYearMonth = selectedYearMonth,
            events = events,
            onClickDay = onClickDay,
        )
    }
}

@Composable
private fun CalendarGrid(
    selectedYearMonth: YearMonth,
    events: Map<LocalDate, Int>,
    onClickDay: (day: LocalDate) -> Unit,
) {
    val localeFirstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value
    val weekDays = remember {
        (0 until DAYS_OF_WEEK)
            .map { DayOfWeek.of((localeFirstDayOfWeek - 1 + it) % DAYS_OF_WEEK + 1) }
            .toList()
    }

    val emptyFieldCount = weekDays.indexOf(selectedYearMonth.firstDay.dayOfWeek.toJavaDayOfWeek())
    val daysInMonth = selectedYearMonth.numberOfDays

    VerticalGrid(
        columns = SimpleGridCells.Fixed(DAYS_OF_WEEK),
        modifier = if (isMediumWidthWindow() && !isExpandedWidthWindow()) {
            Modifier.widthIn(max = 360.dp)
        } else {
            Modifier
        },
    ) {
        weekDays.fastForEach { item ->
            Text(
                text = item.getDisplayName(
                    TextStyle.NARROW,
                    Locale.getDefault(),
                ),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = FontSize,
            )
        }
        repeat(emptyFieldCount) { Box { } }
        repeat(daysInMonth) { dayIndex ->
            val localDate = LocalDate(selectedYearMonth.year, selectedYearMonth.month, dayIndex + 1)
            CalendarDay(
                date = localDate,
                onDayClick = { onClickDay(localDate) },
                events = events[localDate] ?: 0,
            )
        }
    }
}

// KMK -->
@Preview
@Composable
fun CalendarDayPreview() {
    TachiyomiPreviewTheme {
        Surface {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            Calendar(
                selectedYearMonth = today.yearMonth,
                events = mapOf(
                    today to 3,
                    today.plus(1, DateTimeUnit.DAY) to 1,
                    today.minus(15, DateTimeUnit.DAY) to 1,
                ),
                setSelectedYearMonth = {},
                onClickDay = {},
            )
        }
    }
}
// KMK <--
