package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExhUtils(
    isVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    backgroundColor: Color,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = isVisible) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.fillMaxWidth(0.5f).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickRetryAll,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(SYMR.strings.eh_retry_all),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                        TextButton(
                            onClick = onClickRetryAllHelp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "?",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(0.9f).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickBoostPage,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(SYMR.strings.eh_boost_page),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                        TextButton(
                            onClick = onClickBoostPageHelp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "?",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { onSetExhUtilsVisibility(!isVisible) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (isVisible) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = null,
                // KMK -->
                tint = MaterialTheme.colorScheme.primary,
                // KMK <--
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun ExhUtilsPreview() {
    Surface {
        ExhUtils(
            isVisible = true,
            onSetExhUtilsVisibility = {},
            backgroundColor = Color.Black,
            onClickBoostPage = {},
            onClickBoostPageHelp = {},
            onClickRetryAll = {},
            onClickRetryAllHelp = {},
        )
    }
}
