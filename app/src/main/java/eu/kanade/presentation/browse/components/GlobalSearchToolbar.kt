package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    isRunning: Boolean,
    hasPinnedSources: Boolean,
    sourceCategory: String = "",
    sourceCategories: List<String> = emptyList(),
    onChangeSourceCategory: (String) -> Unit = {},
    // KMK <--
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Box {
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                onClickCloseSearch = navigateUp,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                // KMK -->
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            bulkSelectionButton(isRunning, toggleSelectionMode),
                        ),
                    )
                },
                // KMK <--
            )
            if (progress in 1..<total) {
                LinearProgressIndicator(
                    progress = { progress / total.toFloat() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            // TODO: make this UX better; it only applies when triggering a new search
            if (!hideSourceFilter) {
                // KMK -->
                if (hasPinnedSources) {
                    // KMK <--
                    FilterChip(
                        selected = sourceFilter == SourceFilter.PinnedOnly,
                        onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(FilterChipDefaults.IconSize),
                            )
                        },
                        label = {
                            Text(text = stringResource(MR.strings.pinned_sources))
                        },
                    )
                }
                FilterChip(
                    selected = sourceFilter == SourceFilter.All,
                    onClick = { onChangeSearchFilter(SourceFilter.All) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(text = stringResource(MR.strings.all))
                    },
                )

                // KMK --> A category composes with Pinned Only/All instead of replacing them.
                if (sourceCategories.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = sourceCategory.isNotBlank(),
                            onClick = { expanded = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(
                                    text = sourceCategory.ifBlank {
                                        stringResource(KMR.strings.pref_global_search_source_category)
                                    },
                                )
                            },
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(KMR.strings.pref_global_search_source_category_all))
                                },
                                onClick = {
                                    expanded = false
                                    onChangeSourceCategory("")
                                },
                            )
                            sourceCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        expanded = false
                                        onChangeSourceCategory(category)
                                    },
                                )
                            }
                        }
                    }
                }
                // KMK <--

                VerticalDivider()
            }

            FilterChip(
                selected = onlyShowHasResults,
                onClick = { onToggleResults() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier
                            .size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(text = stringResource(MR.strings.has_results))
                },
            )
        }

        HorizontalDivider()
    }
}
