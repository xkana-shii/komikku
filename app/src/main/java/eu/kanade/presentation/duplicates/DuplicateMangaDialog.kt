package eu.kanade.presentation.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import eu.kanade.domain.ui.UiPreferences
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.duplicates.components.DuplicateMangaListItem
import eu.kanade.presentation.duplicates.components.getMaximumMangaCardHeight
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DuplicateMangaDialog(
    duplicates: List<MangaWithChapterCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: (manga: Manga) -> Unit,
    onMigrate: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    targetManga: Manga,
    bulkFavoriteManga: Manga? = null,
    onAllowAllDuplicate: () -> Unit = {},
    onSkipAllDuplicate: () -> Unit = {},
    onSkipDuplicate: () -> Unit = {},
    stopRunning: () -> Unit = {},
    // KMK <--
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val minHeight = LocalPreferenceMinHeight.current
    val horizontalPadding = PaddingValues(horizontal = TabbedDialogPaddings.Horizontal)
    val horizontalPaddingModifier = Modifier.padding(horizontalPadding)

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = {
            // KMK -->
            stopRunning()
            // KMK <--
            onDismissRequest()
        },
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.possible_duplicates_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .then(horizontalPaddingModifier)
                    .padding(top = MaterialTheme.padding.small),
            )

            // KMK -->
            Text(
                text = targetManga.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.then(horizontalPaddingModifier),
            )
            // KMK <--

            Text(
                text = stringResource(MR.strings.possible_duplicates_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.then(horizontalPaddingModifier),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.height(getMaximumMangaCardHeight(duplicates, duplicateMangaDialogCardWidth)),
                contentPadding = horizontalPadding,
            ) {
                // KMK -->
                itemsIndexed(
                    // KMK <--
                    items = duplicates,
                    key = { _, it -> it.manga.id },
                ) { index, it ->
                    DuplicateMangaListItem(
                        duplicate = it,
                        getSource = { sourceManager.getOrStub(it.manga.source) },
                        cardWidth = duplicateMangaDialogCardWidth,
                        onClick = { onMigrate(it.manga) },
                        onLongClick = { onOpenManga(it.manga) },
                    )

                    // KMK -->
                    if (index != duplicates.lastIndex) {
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                    }
                    // KMK <--
                }
            }

            // KMK -->
            if (bulkFavoriteManga != null) {
                Column(
                    modifier = horizontalPaddingModifier
                        .padding(bottom = MaterialTheme.padding.medium),
                ) {
                    HorizontalDivider()

                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onConfirm()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_add_anyway))
                            }

                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onAllowAllDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_allow_all_duplicate_manga))
                            }
                        }

                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onSkipDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_skip_duplicate_manga))
                            }

                            TextButton(
                                onClick = {
                                    onDismissRequest()
                                    onSkipAllDuplicate()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(KMR.strings.action_skip_all_duplicate_manga))
                            }
                        }

                        FlowColumn {
                            TextButton(
                                onClick = {
                                    onOpenManga(bulkFavoriteManga)
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_show_manga))
                            }

                            TextButton(
                                onClick = {
                                    stopRunning()
                                    onDismissRequest()
                                },
                                Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                        }
                    }
                }
            } else {
                // KMK <--
                Column(modifier = horizontalPaddingModifier) {
                    HorizontalDivider()

                    TextPreferenceWidget(
                        title = stringResource(MR.strings.action_add_anyway),
                        icon = Icons.Outlined.Add,
                        onPreferenceClick = {
                            onDismissRequest()
                            onConfirm()
                        },
                        modifier = Modifier.clip(CircleShape),
                    )
                }

                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .then(horizontalPaddingModifier)
                        .padding(bottom = MaterialTheme.padding.medium)
                        .heightIn(min = minHeight)
                        .fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall),
                        text = stringResource(MR.strings.action_cancel),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private val duplicateMangaDialogCardWidth = 150.dp
