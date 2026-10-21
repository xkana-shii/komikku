package eu.kanade.presentation.more.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupCategorySelection
import eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<CreateBackupViewModel>()
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.createBackup(context, it)
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    scope.launch {
                        if (!BackupCreateWorker.isManualJobRunning(context)) {
                            try {
                                chooseBackupDir.launch(BackupCreator.getFilename())
                            } catch (_: ActivityNotFoundException) {
                                context.toast(MR.strings.file_picker_error)
                            }
                        } else {
                            context.toast(MR.strings.backup_in_progress)
                        }
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, viewModel)
                    }
                }

                // KNS -->
                item {
                    SectionCard {
                        CategorySelection(state, viewModel)
                    }
                }
                // KNS <--

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, viewModel)
                    }
                }
            }
        }
    }

    // KNS -->
    /**
     * Which categories the backup contains, collapsed to one row by default so the screen stays short.
     * Nested categories are indented under their parent; the row shows a summary of the selection.
     */
    @Composable
    private fun CategorySelection(state: CreateBackupViewModel.State, model: CreateBackupViewModel) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val selection = state.options.includedCategoryIds
        val enabled = state.options.libraryEntries || state.options.categories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .heightIn(min = 48.dp)
                .padding(horizontal = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = if (expanded) MaterialSymbols.Rounded.KeyboardArrowDown else MaterialSymbols.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
            Text(
                text = stringResource(MR.strings.categories),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    selection == null -> stringResource(MR.strings.all)
                    selection.isEmpty() -> stringResource(MR.strings.none)
                    else -> stringResource(KMR.strings.backup_categories_selected, selection.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                LabeledCheckbox(
                    label = stringResource(KMR.strings.backup_all_categories),
                    checked = selection == null,
                    onCheckedChange = model::selectAllCategories,
                    enabled = enabled,
                )
                BackupCategorySelection.rows(state.categories).forEach { (category, depth) ->
                    LabeledCheckbox(
                        label = if (category.isSystemCategory) stringResource(MR.strings.label_default) else category.name,
                        checked = BackupCategorySelection.isSelected(selection, category.id),
                        onCheckedChange = { model.toggleCategory(category.id, it) },
                        modifier = Modifier.padding(start = 24.dp * depth),
                        enabled = enabled,
                    )
                }
            }
        }
    }
    // KNS <--

    @Composable
    private fun Options(
        options: List<BackupOptions.Entry>,
        state: CreateBackupViewModel.State,
        model: CreateBackupViewModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class CreateBackupViewModel(
    // KNS -->
    getCategories: GetCategories,
    // KNS <--
) : ViewModel() {

    val state: StateFlow<CreateBackupViewModel.State>
        field = MutableStateFlow<CreateBackupViewModel.State>(State())

    // KNS -->
    init {
        viewModelScope.launch {
            val categories = getCategories.await()
            state.update { it.copy(categories = categories) }
        }
    }

    fun selectAllCategories(all: Boolean) {
        state.update { it.copy(options = it.options.copy(includedCategoryIds = BackupCategorySelection.selectAll(all))) }
    }

    fun toggleCategory(categoryId: Long, selected: Boolean) {
        state.update {
            val selection = BackupCategorySelection.toggle(it.options.includedCategoryIds, categoryId, selected, it.categories)
            it.copy(options = it.options.copy(includedCategoryIds = selection))
        }
    }
    // KNS <--

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        state.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    fun createBackup(context: Context, uri: Uri) {
        BackupCreateWorker.startNow(context, uri, state.value.options)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
        // KNS -->
        val categories: List<Category> = emptyList(),
        // KNS <--
    )
}
