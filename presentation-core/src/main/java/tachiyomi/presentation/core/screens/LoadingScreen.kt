package tachiyomi.presentation.core.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import java.text.NumberFormat

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    text: StringResource? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            if (text != null) {
                Text(
                    text = stringResource(text),
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small)
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

@Composable
fun LoadingScreen(
    percentage: Int,
    message: StringResource,
    modifier: Modifier = Modifier,
) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }
    LoadingScreen(modifier = modifier) {
        Text(
            text = stringResource(message, numberFormat.format(percentage / 100f)),
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
