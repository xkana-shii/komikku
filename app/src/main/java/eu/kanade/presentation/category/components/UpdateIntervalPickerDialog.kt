package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
private val intervalChoices = listOf(1, 2, 3, 7, 14, 30)

@Composable
fun UpdateIntervalPickerDialog(
    currentIntervalDays: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.category_update_interval)) },
        text = {
            Column {
                UpdateIntervalChoice(
                    selected = currentIntervalDays == null,
                    label = stringResource(KMR.strings.category_update_interval_inherit),
                    onClick = { onSelect(null) },
                )
                intervalChoices.forEach { days ->
                    UpdateIntervalChoice(
                        selected = currentIntervalDays == days,
                        label = stringResource(KMR.strings.category_update_interval_days, days),
                        onClick = { onSelect(days) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_close)) } },
    )
}

@Composable
private fun UpdateIntervalChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}
// KMK <--
