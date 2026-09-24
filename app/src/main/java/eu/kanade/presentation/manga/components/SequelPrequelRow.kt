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
import eu.kanade.domain.manga.interactor.GetSequelPrequel
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.openInBrowser
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> Transient tracker relations, sharing Houri's card presentation.
@Composable
fun SequelPrequelRow(manga: Manga) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val enabled by remember { uiPreferences.showSequelPrequel().changes() }.collectAsState(uiPreferences.showSequelPrequel().get())
    AnimatedVisibility(
        visible = enabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SequelPrequelContent(manga)
    }
}

@Composable
private fun SequelPrequelContent(manga: Manga) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val preferences = remember { Injekt.get<TrackPreferences>() }
    val tracks by remember(manga.id) { Injekt.get<GetTracks>().subscribe(manga.id) }.collectAsState(null)
    val mangaPreference by remember { preferences.preferredTrackerForManga().changes() }.collectAsState("")
    val priority by remember { preferences.priorityTrackerId().changes() }.collectAsState(preferences.priorityTrackerId().get())
    val loggedInTrackers by remember { Injekt.get<TrackerManager>().loggedInTrackersFlow() }.collectAsState(emptyList())
    val library by remember { Injekt.get<MangaRepository>().getLibraryMangaAsFlow() }.collectAsState(emptyList())
    val libraryIds = remember(library) { library.map { it.manga.id }.toSet() }
    var entries by remember(manga.id) { mutableStateOf(emptyList<SequelPrequelEntry>()) }
    var selected by remember(manga.id) { mutableStateOf<SequelPrequelEntry?>(null) }
    LaunchedEffect(manga.id, tracks?.map { it.trackerId to it.remoteId }, mangaPreference, priority, loggedInTrackers.map { it.id }) {
        val bindings = tracks ?: return@LaunchedEffect
        entries = emptyList()
        try {
            entries = withIOContext { Injekt.get<GetSequelPrequel>().await(manga, bindings) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.xLogE("Failed to load tracker relations", e)
        }
    }
    AnimatedVisibility(
        visible = entries.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Text(
                text = stringResource(KMR.strings.pref_sequel_prequel_title),
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                contentPadding = PaddingValues(MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                items(entries, key = { "${it.relation}-${it.url}" }) { entry ->
                    SequelPrequelCard(entry, entry.localMangaId in libraryIds) {
                        entry.localMangaId?.let { navigator.push(MangaScreen(it)) } ?: run { selected = entry }
                    }
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
private fun SequelPrequelCard(entry: SequelPrequelEntry, inLibrary: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick).padding(vertical = MaterialTheme.padding.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = stringResource(
                when (entry.relation) {
                    SequelPrequelRelation.PREQUEL -> KMR.strings.relation_prequel
                    SequelPrequelRelation.SEQUEL -> KMR.strings.relation_sequel
                    SequelPrequelRelation.ADAPTATION -> KMR.strings.relation_adaptation
                    SequelPrequelRelation.ALTERNATIVE -> KMR.strings.relation_alternative
                    SequelPrequelRelation.SIDE_STORY -> KMR.strings.relation_side_story
                    SequelPrequelRelation.SPIN_OFF -> KMR.strings.relation_spin_off
                    SequelPrequelRelation.PARENT -> KMR.strings.relation_parent
                    SequelPrequelRelation.SUMMARY -> KMR.strings.relation_summary
                    SequelPrequelRelation.CAMEO -> KMR.strings.relation_cameo
                    SequelPrequelRelation.CHARACTER_FOCUS -> KMR.strings.relation_character_focus
                    SequelPrequelRelation.COMPILATION -> KMR.strings.relation_compilation
                    SequelPrequelRelation.CONTAINS -> KMR.strings.relation_contains
                    SequelPrequelRelation.CROSSOVER -> KMR.strings.relation_crossover
                    SequelPrequelRelation.EXPANSION -> KMR.strings.relation_expansion
                    SequelPrequelRelation.MAIN -> KMR.strings.relation_main
                    SequelPrequelRelation.MAIN_STORY -> KMR.strings.relation_main_story
                    SequelPrequelRelation.PARODY -> KMR.strings.relation_parody
                    SequelPrequelRelation.REBOOT -> KMR.strings.relation_reboot
                    SequelPrequelRelation.REMAKE -> KMR.strings.relation_remake
                    SequelPrequelRelation.SAME_UNIVERSE -> KMR.strings.relation_same_universe
                    SequelPrequelRelation.SERIES -> KMR.strings.relation_series
                    SequelPrequelRelation.SOURCE -> KMR.strings.relation_source
                    SequelPrequelRelation.UNCOLLECTED -> KMR.strings.relation_uncollected
                    SequelPrequelRelation.DOUJINSHI -> KMR.strings.relation_doujinshi
                    SequelPrequelRelation.COLORED -> KMR.strings.relation_colored
                    SequelPrequelRelation.MONOCHROME -> KMR.strings.relation_monochrome
                    SequelPrequelRelation.ALTERNATE_STORY -> KMR.strings.relation_alternate_story
                    SequelPrequelRelation.ALTERNATE_VERSION -> KMR.strings.relation_alternate_version
                    SequelPrequelRelation.PRESERIALIZATION -> KMR.strings.relation_preserialization
                    SequelPrequelRelation.SERIALIZATION -> KMR.strings.relation_serialization
                    SequelPrequelRelation.OTHER -> KMR.strings.relation_other
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.coverUrl?.let {
            MangaCover.Book(data = it, modifier = Modifier.width(96.dp), contentDescription = entry.title)
        }
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (inLibrary) {
            Text(stringResource(MR.strings.in_library), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
        }
    }
}
// KMK <--
