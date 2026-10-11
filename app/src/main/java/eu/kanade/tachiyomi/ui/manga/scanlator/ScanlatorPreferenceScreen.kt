package eu.kanade.tachiyomi.ui.manga.scanlator

// KMK -->
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.ChapterBlacklistEntries
import eu.kanade.presentation.manga.ChapterBlacklistEntry
import eu.kanade.presentation.manga.resolveChapterBlacklistEntries
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.chapter.ScanlatorCoverage
import eu.kanade.tachiyomi.util.chapter.ScanlatorRangeRule
import eu.kanade.tachiyomi.util.chapter.encodeScanlatorRangeRule
import eu.kanade.tachiyomi.util.chapter.parseScanlatorRangeRule
import eu.kanade.tachiyomi.util.chapter.scanlatorCoverage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ScanlatorPreferenceScreen(
    private val mangaId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ScanlatorPreferenceScreenModel(mangaId) }
        val state by screenModel.state.collectAsState()
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.scanlator_preference),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(KMR.strings.scanlator_priority)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(KMR.strings.blacklisted_chapters)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(KMR.strings.scanlator_ranges)) },
                    )
                }
                when (selectedTab) {
                    0 -> PriorityTab(state, screenModel)
                    1 -> BlacklistTab(state, screenModel)
                    else -> RangesTab(state, screenModel)
                }
            }
        }
    }

    @Composable
    private fun PriorityTab(
        state: ScanlatorPreferenceScreenModel.State,
        screenModel: ScanlatorPreferenceScreenModel,
    ) {
        val listState = rememberLazyListState()
        val dragList = remember { state.orderedScanlators.toMutableStateList() }
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            dragList.apply { add(to.index, removeAt(from.index)) }
            screenModel.setPriority(dragList.map(ScanlatorEntry::name))
        }
        LaunchedEffect(state.orderedScanlators) {
            if (!reorderableState.isAnyItemDragging) {
                dragList.clear()
                dragList.addAll(state.orderedScanlators)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(dragList, key = ScanlatorEntry::name) { entry ->
                ReorderableItem(reorderableState, entry.name) {
                    val excluded = entry.name in state.excludedScanlators
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .animateItem(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .draggableHandle(),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(KMR.strings.scanlator_chapter_count, entry.count),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                entry.coverage?.let { coverage ->
                                    Text(
                                        text = coverage.groups.joinToString(", ") +
                                            if (coverage.hiddenCount == 0) {
                                                ""
                                            } else {
                                                ", ${stringResource(KMR.strings.scanlator_coverage_more, coverage.hiddenCount)}"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { screenModel.toggleExcluded(entry.name) }) {
                                Icon(
                                    imageVector = if (excluded) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = stringResource(KMR.strings.action_exclude_scanlator),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BlacklistTab(
        state: ScanlatorPreferenceScreenModel.State,
        screenModel: ScanlatorPreferenceScreenModel,
    ) {
        ChapterBlacklistEntries(
            entries = state.blacklist,
            onRemove = screenModel::removeBlacklisted,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        )
    }

    @Composable
    private fun RangesTab(
        state: ScanlatorPreferenceScreenModel.State,
        screenModel: ScanlatorPreferenceScreenModel,
    ) {
        var showAddDialog by remember { mutableStateOf(false) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item(key = "add-range") {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(KMR.strings.scanlator_range_add))
                }
            }
            if (state.rangeRules.isEmpty()) {
                item(key = "ranges-empty") {
                    Text(
                        text = stringResource(KMR.strings.scanlator_ranges_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.rangeRules, key = { it }) { raw ->
                parseScanlatorRangeRule(raw)?.let { rule ->
                    RangeRuleItem(rule, onRemove = { screenModel.removeRangeRule(raw) })
                }
            }
        }
        if (showAddDialog) {
            RangeRuleDialog(
                scanlators = state.orderedScanlators.map(ScanlatorEntry::name),
                onConfirm = { from, to, scanlator ->
                    screenModel.addRangeRule(from, to, scanlator)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }

    @Composable
    private fun RangeRuleItem(
        rule: ScanlatorRangeRule,
        onRemove: () -> Unit,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${rule.from.asChapterLabel()} – ${rule.to.asChapterLabel()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(rule.scanlator, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, stringResource(MR.strings.action_remove))
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun RangeRuleDialog(
        scanlators: List<String>,
        onConfirm: (Double, Double, String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var fromText by remember { mutableStateOf("") }
        var toText by remember { mutableStateOf("") }
        var selectedScanlator by remember { mutableStateOf<String?>(null) }
        val from = fromText.toDoubleOrNull()
        val to = toText.toDoubleOrNull()
        val valid = from != null && to != null && from.isFinite() && to.isFinite() && from <= to &&
            selectedScanlator != null

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(KMR.strings.scanlator_range_add)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it },
                        label = { Text(stringResource(KMR.strings.scanlator_range_from)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it },
                        label = { Text(stringResource(KMR.strings.scanlator_range_to)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Text(
                        stringResource(KMR.strings.scanlator_range_scanlator),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        scanlators.forEach { scanlator ->
                            FilterChip(
                                selected = scanlator == selectedScanlator,
                                onClick = { selectedScanlator = scanlator },
                                label = { Text(scanlator) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val scanlator = selectedScanlator
                TextButton(
                    enabled = valid,
                    onClick = {
                        if (from != null && to != null && scanlator != null) {
                            onConfirm(from, to, scanlator)
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private fun Double.asChapterLabel(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

data class ScanlatorEntry(
    val name: String,
    val count: Int,
    val coverage: ScanlatorCoverage?,
)

class ScanlatorPreferenceScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
) : StateScreenModel<ScanlatorPreferenceScreenModel.State>(State()) {

    data class State(
        val orderedScanlators: List<ScanlatorEntry> = emptyList(),
        val excludedScanlators: Set<String> = emptySet(),
        val blacklist: List<ChapterBlacklistEntry> = emptyList(),
        val rangeRules: List<String> = emptyList(),
    )

    init {
        screenModelScope.launch {
            combine(
                getManga.subscribe(mangaId),
                getChaptersByMangaId.subscribe(mangaId),
            ) { manga, chapters -> manga to chapters }
                .collectLatest { (manga, chapters) ->
                    val chaptersByScanlator = chapters
                        .mapNotNull { chapter ->
                            chapter.scanlator?.trim()?.takeIf { it.isNotEmpty() }?.let { it to chapter }
                        }
                        .groupBy({ it.first }, { it.second })
                    val configured = manga.scanlatorPriority.filter { it in chaptersByScanlator }
                    val remaining = chaptersByScanlator.keys.filterNot { it in configured }.sorted()
                    mutableState.update {
                        it.copy(
                            orderedScanlators = (configured + remaining).map { scanlator ->
                                val scanlatorChapters = chaptersByScanlator.getValue(scanlator)
                                ScanlatorEntry(
                                    name = scanlator,
                                    count = scanlatorChapters.size,
                                    coverage = scanlatorCoverage(scanlatorChapters.map(Chapter::chapterNumber)),
                                )
                            },
                            blacklist = resolveChapterBlacklistEntries(manga.blacklistedChapters, chapters),
                            rangeRules = manga.scanlatorRangeRules,
                        )
                    }
                }
        }
        screenModelScope.launch {
            getExcludedScanlators.subscribe(mangaId).collectLatest { excluded ->
                mutableState.update { it.copy(excludedScanlators = excluded.toSet()) }
            }
        }
    }

    fun setPriority(scanlators: List<String>) {
        screenModelScope.launch {
            updateManga.await(MangaUpdate(id = mangaId, scanlatorPriority = scanlators))
        }
    }

    fun toggleExcluded(scanlator: String) {
        screenModelScope.launch {
            val excluded = state.value.excludedScanlators
            setExcludedScanlators.await(
                mangaId,
                if (scanlator in excluded) excluded - scanlator else excluded + scanlator,
            )
        }
    }

    fun removeBlacklisted(key: String) {
        screenModelScope.launch {
            updateManga.await(
                MangaUpdate(
                    id = mangaId,
                    blacklistedChapters = state.value.blacklist.map(ChapterBlacklistEntry::key) - key,
                ),
            )
        }
    }

    fun addRangeRule(from: Double, to: Double, scanlator: String) {
        if (!from.isFinite() || !to.isFinite() || from > to) return
        screenModelScope.launch {
            val rule = encodeScanlatorRangeRule(ScanlatorRangeRule(from, to, scanlator))
            val rules = state.value.rangeRules
            if (rule !in rules) {
                updateManga.await(MangaUpdate(id = mangaId, scanlatorRangeRules = rules + rule))
            }
        }
    }

    fun removeRangeRule(rule: String) {
        screenModelScope.launch {
            updateManga.await(
                MangaUpdate(id = mangaId, scanlatorRangeRules = state.value.rangeRules - rule),
            )
        }
    }
}
// KMK <--
