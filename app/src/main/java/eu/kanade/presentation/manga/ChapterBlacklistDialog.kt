package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
@Composable
fun ChapterBlacklistDialog(
    entries: List<String>,
    onDismissRequest: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.blacklisted_chapters)) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(KMR.strings.blacklist_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = { it }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = entry.removePrefix("url:"),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp),
                            )
                            IconButton(onClick = { onRemove(entry) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(MR.strings.action_remove),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_close))
            }
        },
    )
}
// KMK <--
