package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.visualName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

// KMK -->
@Composable
fun CategoryMoveDialog(
    parents: ImmutableList<Category>,
    excludedIds: Set<Long>,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selected by remember { mutableStateOf(parents.firstOrNull { it.id !in excludedIds }?.id) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    selected?.let(onConfirm)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(KMR.strings.category_manager_move))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(stringResource(KMR.strings.category_manager_move_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                parents.forEach { parent ->
                    val enabled = parent.id !in excludedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { selected = parent.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == parent.id,
                            enabled = enabled,
                            onClick = { selected = parent.id },
                        )
                        Text(
                            text = parent.name,
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun CategoryMergeDialog(
    candidates: ImmutableList<Category>,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selected by remember { mutableStateOf(candidates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = selected != null && candidates.size >= 2,
                onClick = {
                    selected?.let(onConfirm)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(KMR.strings.category_manager_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(stringResource(KMR.strings.category_manager_merge_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = candidate.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == candidate.id,
                            onClick = { selected = candidate.id },
                        )
                        Text(
                            text = candidate.name,
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                        )
                    }
                }
            }
        },
    )
}
// KMK <--

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    categories: ImmutableList<String>,
    // SY -->
    title: String = stringResource(MR.strings.action_add_category),
    extraMessage: String? = null,
    alreadyExistsError: StringResource = MR.strings.error_category_exists,
    // SY <--
) {
    var name by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    // KMK --> Keep UI feedback aligned with the sibling validation in the interactor.
    val normalizedName = remember(name) { name.trim() }
    val nameAlreadyExists = remember(name, categories) {
        categories.any { it.trim().equals(normalizedName, ignoreCase = true) }
    }
    // KMK <--

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty() && normalizedName.length <= 50 && !nameAlreadyExists,
                onClick = {
                    onCreate(normalizedName)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Column {
                extraMessage?.let { Text(it) }
                // SY <--

                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    supportingText = {
                        val msgRes = if (normalizedName.isNotEmpty() && nameAlreadyExists) {
                            // SY -->
                            alreadyExistsError
                            // SY <--
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = normalizedName.isNotEmpty() && nameAlreadyExists,
                    singleLine = true,
                )
                // SY -->
            }
            // SY <--
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    categories: ImmutableList<String>,
    category: String,
) {
    var name by remember { mutableStateOf(category) }
    var valueHasChanged by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    // KMK --> Keep UI feedback aligned with the sibling validation in the interactor.
    val normalizedName = remember(name) { name.trim() }
    val nameAlreadyExists = remember(name, categories) {
        categories.any { it.trim().equals(normalizedName, ignoreCase = true) }
    }
    // KMK <--

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = valueHasChanged && normalizedName.isNotEmpty() && normalizedName.length <= 50 && !nameAlreadyExists,
                onClick = {
                    onRename(normalizedName)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    // SY -->
    category: String = "",
    title: String = stringResource(MR.strings.delete_category),
    text: String = stringResource(MR.strings.delete_category_confirmation, category),
    // SY <--
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Text(text = text)
            // SY <--
        },
    )
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: ImmutableList<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }
    var selection by remember { mutableStateOf(initialSelection) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row {
                tachiyomi.presentation.core.components.material.TextButton(onClick = {
                    onDismissRequest()
                    onEditCategories()
                }) {
                    Text(text = stringResource(MR.strings.action_edit))
                }
                Spacer(modifier = Modifier.weight(1f))
                tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        // KMK --> A selected subcategory must persist together with its parent.
                        val (included, excluded) = categorySelectionChange(selection)
                        onConfirm(
                            included,
                            excluded,
                        )
                        // KMK <--
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_move_category))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // KMK --> Keep manager order while grouping each child immediately after its parent.
                orderedCategorySelection(selection).forEach { checkbox ->
                    // KMK <--
                    val onChange: (CheckboxState<Category>) -> Unit = {
                        val index = selection.indexOf(it)
                        if (index != -1) {
                            val mutableList = selection.toMutableList()
                            mutableList[index] = it.next()
                            selection = mutableList.toList().toImmutableList()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // KMK --> Indent subcategories without changing root alignment.
                            .padding(
                                start = if (checkbox.value.parentId != Category.UNCATEGORIZED_ID) {
                                    MaterialTheme.padding.medium
                                } else {
                                    0.dp
                                },
                            )
                            // KMK <--
                            .clickable { onChange(checkbox) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (checkbox) {
                            is CheckboxState.TriState -> {
                                TriStateCheckbox(
                                    state = checkbox.asToggleableState(),
                                    onClick = { onChange(checkbox) },
                                )
                            }
                            is CheckboxState.State -> {
                                Checkbox(
                                    checked = checkbox.isChecked,
                                    onCheckedChange = { onChange(checkbox) },
                                )
                            }
                        }

                        Text(
                            text = checkbox.value.visualName,
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                        )
                    }
                }
            }
        },
    )
}

// KMK --> Shared pure dialog logic keeps hierarchy handling identical across category flows.
internal fun orderedCategorySelection(
    selection: List<CheckboxState<Category>>,
): List<CheckboxState<Category>> {
    val childrenByParent = selection
        .filter { it.value.parentId != Category.UNCATEGORIZED_ID }
        .groupBy { it.value.parentId }
    val ordered = mutableListOf<CheckboxState<Category>>()
    val includedIds = mutableSetOf<Long>()

    fun addBranch(checkbox: CheckboxState<Category>) {
        if (!includedIds.add(checkbox.value.id)) return
        ordered += checkbox
        childrenByParent[checkbox.value.id].orEmpty().forEach(::addBranch)
    }

    selection.filter { it.value.parentId == Category.UNCATEGORIZED_ID }.forEach(::addBranch)
    selection.forEach(::addBranch)
    return ordered
}

internal fun categorySelectionChange(
    selection: List<CheckboxState<Category>>,
): Pair<List<Long>, List<Long>> {
    val parentIdById = selection.associate { it.value.id to it.value.parentId }
    val included = linkedSetOf<Long>()
    selection
        .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
        .forEach { included += it.value.id.withAncestorChain(parentIdById) }
    val excluded = selection
        .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
        .mapTo(linkedSetOf()) { it.value.id }
        .apply { removeAll(included) }
    return included.toList() to excluded.toList()
}

/** Returns [id] with each known ancestor, protecting malformed imported cycles. */
internal fun Long.withAncestorChain(parentIdById: Map<Long, Long>): Set<Long> {
    val chain = linkedSetOf(this)
    var cursor = parentIdById[this] ?: Category.UNCATEGORIZED_ID
    while (cursor != Category.UNCATEGORIZED_ID && chain.add(cursor)) {
        cursor = parentIdById[cursor] ?: Category.UNCATEGORIZED_ID
    }
    return chain
}
// KMK <--
