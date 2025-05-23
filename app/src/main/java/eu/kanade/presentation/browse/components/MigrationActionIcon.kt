package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MigrationActionIcon(
    modifier: Modifier,
    result: MigratingManga.SearchResult,
    skipManga: () -> Unit,
    // KMK -->
    cancelManga: () -> Unit,
    // KMK <--
    searchManually: () -> Unit,
    migrateNow: () -> Unit,
    copyNow: () -> Unit,
) {
    Box(modifier) {
        if (result is MigratingManga.SearchResult.Searching) {
            // KMK -->
            IconButton(onClick = cancelManga) {
                // KMK <--
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(SYMR.strings.action_stop),
                )
            }
        } else if (result is MigratingManga.SearchResult.Result || result is MigratingManga.SearchResult.NotFound) {
            Column {
                IconButton(onClick = searchManually) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(SYMR.strings.action_search_manually),
                    )
                }
                IconButton(onClick = skipManga) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = stringResource(SYMR.strings.action_skip_entry),
                    )
                }
                if (result is MigratingManga.SearchResult.Result) {
                    IconButton(onClick = migrateNow) {
                        Icon(
                            imageVector = Icons.Outlined.Done,
                            contentDescription = stringResource(SYMR.strings.action_migrate_now),
                        )
                    }
                    IconButton(onClick = copyNow) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(SYMR.strings.action_copy_now),
                        )
                    }
                }
            }
        }
    }
}
