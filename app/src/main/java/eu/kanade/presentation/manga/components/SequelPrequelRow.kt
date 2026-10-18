package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.openInBrowser
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
@Composable
fun SequelPrequelRow(manga: Manga) {
    val context = LocalContext.current
    val graph = context.appGraph
    val enabled by graph.uiPreferences.showSequelPrequel.changes().collectAsState(
        initial = graph.uiPreferences.showSequelPrequel.get(),
    )
    val tracks by remember(manga.id) { graph.getTracks.subscribe(manga.id) }.collectAsState(emptyList())
    var entries by remember(manga.id) { mutableStateOf(emptyList<SequelPrequelEntry>()) }

    LaunchedEffect(manga.id, tracks.map { it.trackerId to it.remoteId }) {
        entries = try {
            withIOContext { graph.getSequelPrequel.await(manga, tracks) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.xLogE("Failed to load tracker relations", e)
            emptyList()
        }
    }

    AnimatedVisibility(
        visible = enabled && entries.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SequelPrequelContent(entries)
    }
}

@Composable
private fun SequelPrequelContent(entries: List<SequelPrequelEntry>) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    var selected by remember(entries) { mutableStateOf<SequelPrequelEntry?>(null) }

    Column {
        Text(
            text = stringResource(KMR.strings.pref_sequel_prequel_title),
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            style = MaterialTheme.typography.titleMedium,
        )
        // Keep the header aligned to the manga page while the cover strip can use its full width.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            items(entries, key = { "${it.relation}-${it.url}" }) { entry ->
                SequelPrequelCard(entry) {
                    entry.localMangaId?.let { navigator.push(MangaScreen(it)) } ?: run { selected = entry }
                }
            }
        }
    }
    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entry.title) },
            text = { Text(stringResource(KMR.strings.relation_find_source)) },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    navigator.push(GlobalSearchScreen(entry.title))
                }) { Text(stringResource(MR.strings.action_search)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selected = null
                    context.openInBrowser(entry.url)
                }) { Text(stringResource(MR.strings.action_open_in_browser)) }
            },
        )
    }
}

@Composable
private fun SequelPrequelCard(entry: SequelPrequelEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = entry.relation.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.coverUrl?.let { coverUrl ->
            MangaCover.Book(data = coverUrl, modifier = Modifier.width(96.dp), contentDescription = entry.title)
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
// KMK <--
