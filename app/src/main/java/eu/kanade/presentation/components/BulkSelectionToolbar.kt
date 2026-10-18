package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import mihon.icons.materialsymbols.roundedfilled.Favorite
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BulkSelectionToolbar(
    selectedCount: Int,
    isRunning: Boolean,
    onClickClearSelection: () -> Unit,
    onChangeCategoryClick: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onReverseSelection: (() -> Unit)? = null,
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                actions = buildList {
                    if (onSelectAll != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = MaterialSymbols.Rounded.SelectAll,
                                onClick = onSelectAll,
                            ),
                        )
                    }
                    if (onReverseSelection != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = MaterialSymbols.Rounded.FlipToBack,
                                onClick = onReverseSelection,
                            ),
                        )
                    }
                    if (isRunning) {
                        add(
                            AppBar.ActionCompose(
                                title = stringResource(KMR.strings.action_bulk_select),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            },
                        )
                    } else {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.add_to_library),
                                icon = MaterialSymbols.RoundedFilled.Favorite,
                                onClick = {
                                    if (selectedCount > 0) {
                                        onChangeCategoryClick()
                                    }
                                },
                            ),
                        )
                    }
                },
            )
        },
        isActionMode = true,
        onCancelActionMode = onClickClearSelection,
    )
}

@Preview
@Composable
private fun SelectionToolbarPreview() {
    Column {
        BulkSelectionToolbar(
            selectedCount = 9,
            isRunning = false,
            onClickClearSelection = {},
            onChangeCategoryClick = {},
        )
        BulkSelectionToolbar(
            selectedCount = 9,
            isRunning = true,
            onClickClearSelection = {},
            onChangeCategoryClick = {},
        )
    }
}
