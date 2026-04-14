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
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
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
    currentSavedSearches: ImmutableList<String>,
    saveSearch: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

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
                TextButton(
                    onClick = { showOverwriteDialog = false },
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(SYMR.strings.save_search))
            },
            text = {
                Text(text = stringResource(SYMR.strings.save_search_overwrite_confirm))
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
        )
    } else {
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
                        if (searchName.isBlank()) {
                            context.toast(SYMR.strings.save_search_invalid_name)
                        } else if (searchName in currentSavedSearches) {
                            showOverwriteDialog = true
                        } else {
                            saveSearch(searchName)
                            onDismissRequest()
                        }
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
}
