package eu.kanade.presentation.more.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.storage.StorageOverview
import eu.kanade.tachiyomi.ui.storage.StorageViewModel
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

// KNS -->
@Composable
fun StorageScreenContent(
    state: StorageViewModel.State.Success,
    paddingValues: PaddingValues,
    onCategorySelected: (Long) -> Unit,
    onDelete: (StorageViewModel.Item, Boolean) -> Unit,
    onClickCover: (StorageViewModel.Item) -> Unit,
) {
    LazyColumn(
        contentPadding = paddingValues,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        item(key = "category") {
            StorageCategorySelect(
                state.categories,
                state.selectedCategoryId,
                onCategorySelected,
            )
        }

        if (state.items.isEmpty()) {
            // Avoid nesting EmptyScreen's verticalScroll inside LazyColumn.
            // LazyColumn already provides vertical scrolling.
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.storage_overview_no_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "total") {
            StorageTotal(state.items)
        }

        items(
            items = state.items,
            key = { "storage-${it.entry.manga.id}" },
        ) { item ->
            StorageRow(
                item = item,
                onDelete = { onDelete(item, it) },
                onClickCover = { onClickCover(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun StorageCategorySelect(
    categories: List<Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    val all = stringResource(MR.strings.all)
    val default = stringResource(MR.strings.label_default)

    val options = remember(categories, all, default) {
        listOf(
            StorageOverview.ALL_CATEGORY_ID to all,
            Category.UNCATEGORIZED_ID to default,
        ) + categories.map { it.id to it.name }
    }

    SelectItem(
        label = stringResource(MR.strings.categories),
        options = options.map { it.second }.toTypedArray(),
        selectedIndex = options.indexOfFirst {
            it.first == selectedId
        }.coerceAtLeast(0),
        onSelect = { onSelect(options[it].first) },
    )
}

/** A half-ring split by entry with the total size in the middle. */
@Composable
private fun StorageTotal(items: List<StorageViewModel.Item>) {
    val total = remember(items) {
        items.sumOf { it.entry.size }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.large),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
        ) {
            val stroke = 15.dp.toPx()
            val inset = stroke / 2
            var start = 180f

            items.forEach { item ->
                val sweep = item.entry.size.toFloat() / total * 180f

                drawArc(
                    color = item.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.width - stroke,
                    ),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Butt,
                    ),
                )

                start += sweep
            }
        }

        Text(
            text = StorageOverview.formatSize(total),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(
                bottom = MaterialTheme.padding.medium,
            ),
        )
    }
}

@Composable
private fun StorageRow(
    item: StorageViewModel.Item,
    onDelete: (removeFromLibrary: Boolean) -> Unit,
    onClickCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    val manga = item.entry.manga

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            MaterialTheme.padding.medium,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier.height(48.dp),
            data = manga.asMangaCover(),
            contentDescription = manga.title,
            onClick = onClickCover,
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(item.color, CircleShape),
                )

                Spacer(
                    modifier = Modifier.width(
                        MaterialTheme.padding.small,
                    ),
                )

                Text(
                    text = StorageOverview.formatSize(item.entry.size) +
                        " · " +
                        pluralStringResource(
                            MR.plurals.manga_num_chapters,
                            item.entry.chapterCount,
                            item.entry.chapterCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        IconButton(
            onClick = { showDeleteDialog = true },
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Delete,
                contentDescription = stringResource(
                    MR.strings.action_delete,
                ),
            )
        }
    }

    if (showDeleteDialog) {
        var removeFromLibrary by remember {
            mutableStateOf(false)
        }

        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text(
                    stringResource(
                        if (manga.isLocal()) {
                            KMR.strings.storage_delete_local_manga
                        } else {
                            MR.strings.delete_downloads_for_manga
                        },
                    ),
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.small,
                    ),
                ) {
                    Text(
                        stringResource(
                            KMR.strings.storage_delete_confirm,
                            manga.title,
                        ),
                    )

                    LabeledCheckbox(
                        label = stringResource(
                            MR.strings.remove_from_library,
                        ),
                        checked = removeFromLibrary,
                        onCheckedChange = {
                            removeFromLibrary = it
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(removeFromLibrary)
                        showDeleteDialog = false
                    },
                ) {
                    Text(
                        stringResource(MR.strings.action_delete),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    },
                ) {
                    Text(
                        stringResource(MR.strings.action_cancel),
                    )
                }
            },
        )
    }
}
// KNS <--
