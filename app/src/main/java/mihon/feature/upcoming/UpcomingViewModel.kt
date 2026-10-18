package mihon.feature.upcoming

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.util.insertSeparatorsReversed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpcomingViewModel(
    private val getUpcomingManga: GetUpcomingManga,
    val getCategories: GetCategories,
    val upcomingPreferences: UpcomingPreferences,
    // KMK -->
    private val libraryPreferences: LibraryPreferences,
    // KMK <--
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    val excludedCategories = upcomingPreferences.filterExcludedCategories
    val includedCategories = upcomingPreferences.filterIncludedCategories

    private val selectedYearMonth = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonth,
    )

    private val dialog = MutableStateFlow<Dialog?>(null)

    // KMK -->
    private val showingUpdatingMangas = MutableStateFlow(false)
    // KMK <--

    private val upcoming = getUpcomingItemPreferenceFlow()
        .distinctUntilChanged()
        .flatMapLatest {
            getUpcomingManga.subscribe(
                excludedCategories = it.filterExcludedCategories,
                includedCategories = it.filterIncludedCategories,
            )
        }
        .distinctUntilChanged()
        .map { it.toUpcomingUIModels() }
        .flowOn(Dispatchers.IO)

    // KMK -->
    private val updating = flow { emit(getUpcomingManga.updatingMangas()) }
        .map { it.toUpcomingUIModels() }
        .flowOn(Dispatchers.IO)
    // KMK <--

    private val hasActiveFilters = getUpcomingItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterIncludedCategories,
                prefs.filterExcludedCategories,
            ).any { it.isNotEmpty() }
        }
        .distinctUntilChanged()

    val state: StateFlow<State> = combine(
        combine(
            upcoming,
            // KMK -->
            updating,
            // KMK <--
            hasActiveFilters,
            selectedYearMonth,
            dialog,
        ) { upcoming, updating, hasActiveFilters, selectedYearMonth, dialog ->
            State(
                selectedYearMonth = selectedYearMonth,
                items = upcoming,
                events = upcoming.toEvents(),
                headerIndexes = upcoming.getHeaderIndexes(),
                // KMK -->
                isLoadingUpcoming = false,
                updatingItems = updating,
                updatingEvents = updating.toEvents(),
                updatingHeaderIndexes = updating.getHeaderIndexes(),
                isLoadingUpdating = false,
                // KMK <--
                hasActiveFilters = hasActiveFilters,
                dialog = dialog,
            )
        },
        // KMK -->
        showingUpdatingMangas,
        // KMK <--
    ) { partial, isShowingUpdatingMangas ->
        partial.copy(isShowingUpdatingMangas = isShowingUpdatingMangas)
    }
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State())

    private fun List<Manga>.toUpcomingUIModels(): List<UpcomingUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date
                val afterDate = after?.manga
                    ?.expectedNextUpdate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
            .toList()
    }

    private fun List<UpcomingUIModel>.toEvents(): Map<LocalDate, Int> {
        return filterIsInstance<UpcomingUIModel.Header>()
            .associate { it.date to it.mangaCount }
            .toMap()
    }

    private fun List<UpcomingUIModel>.getHeaderIndexes(): Map<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
            .toMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        selectedYearMonth.value = yearMonth
    }

    private fun getUpcomingItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            upcomingPreferences.filterExcludedCategories.changes(),
            upcomingPreferences.filterIncludedCategories.changes(),
        ) { excluded, included ->
            ItemPreferences(
                filterExcludedCategories = excluded,
                filterIncludedCategories = included,
            )
        }
    }

    fun resetDialog() {
        dialog.value = null
    }

    fun showFilterDialog() {
        dialog.value = Dialog.FilterSheet
    }

    fun cycleCategory(category: Category) {
        when (category.id) {
            in includedCategories.get() -> {
                includedCategories.getAndSet { it - category.id }
                excludedCategories.getAndSet { it + category.id }
            }

            in excludedCategories.get() -> excludedCategories.getAndSet { it - category.id }
            else -> includedCategories.getAndSet { it + category.id }
        }
    }

    @Immutable
    private data class ItemPreferences(
        val filterExcludedCategories: List<Long>,
        val filterIncludedCategories: List<Long>,
    )

    // KMK -->
    val restriction by lazy { libraryPreferences.autoUpdateMangaRestrictions.get() }

    fun showUpdatingMangas() {
        showingUpdatingMangas.value = true
    }

    fun hideUpdatingMangas() {
        showingUpdatingMangas.value = false
    }
    // KMK <--

    data class State(
        val selectedYearMonth: YearMonth = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .yearMonth,
        val items: List<UpcomingUIModel> = listOf(),
        val events: Map<LocalDate, Int> = mapOf(),
        val headerIndexes: Map<LocalDate, Int> = mapOf(),
        // KMK -->
        val isLoadingUpcoming: Boolean = true,
        val isShowingUpdatingMangas: Boolean = false,
        val updatingItems: List<UpcomingUIModel> = listOf(),
        val updatingEvents: Map<LocalDate, Int> = mapOf(),
        val updatingHeaderIndexes: Map<LocalDate, Int> = mapOf(),
        val isLoadingUpdating: Boolean = true,
        // KMK <--
        val hasActiveFilters: Boolean = false,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object FilterSheet : Dialog
    }
}
