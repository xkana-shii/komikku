package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.UpdateSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RemoveMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    mangaToRemove: Manga,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_remove))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_manga, mangaToRemove.title))
        },
    )
}

@Composable
fun SavedSearchDeleteDialog(
    onDismissRequest: () -> Unit,
    name: String,
    deleteSavedSearch: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    deleteSavedSearch()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(SYMR.strings.save_search_delete))
        },
        text = {
            Text(text = stringResource(SYMR.strings.save_search_delete_message, name))
        },
    )
}

@Composable
fun SavedSearchCreateDialog(
    onDismissRequest: () -> Unit,
    currentSavedSearches: List<String>,
    saveSearch: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val context = LocalContext.current
    // KNS --> an existing name asks to overwrite that saved search (as on the previous personal fork)
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveSearch(textFieldValue.text.trim())
                        showOverwriteDialog = false
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = { Text(text = stringResource(SYMR.strings.save_search)) },
            text = { Text(text = stringResource(KMR.strings.save_search_overwrite_confirm)) },
            properties = DialogProperties(usePlatformDefaultWidth = true),
        )
        return
    }
    // KNS <--
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(SYMR.strings.save_search)) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(text = stringResource(SYMR.strings.save_search_hint))
                },
            )
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    val searchName = textFieldValue.text.trim()
                    // KNS -->
                    when {
                        searchName.isBlank() -> context.toast(SYMR.strings.save_search_invalid_name)
                        UpdateSavedSearch.existingFor(searchName, currentSavedSearches) { it } != null ->
                            showOverwriteDialog = true
                        else -> {
                            saveSearch(searchName)
                            onDismissRequest()
                        }
                    }
                    // KNS <--
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
    )
}
