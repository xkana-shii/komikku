package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.duplicates.ManageDuplicatesContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

class ManageDuplicatesScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ManageDuplicatesScreenModel(context) }
        val onDismissRequest = { screenModel.setDialog(null) }
        val lazyListState = rememberLazyListState()
        val state by screenModel.state.collectAsState()
        val haptic = LocalHapticFeedback.current
        val duplicatesListState by screenModel.duplicatesListState.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_manage_duplicates),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (!state.loading && duplicatesListState.isEmpty()) {
                EmptyScreen(MR.strings.information_empty_manage_duplicates, happyFace = true)
                return@Scaffold
            }

            Column {
                ManageDuplicatesContent(
                    duplicatesLists = duplicatesListState,
                    paddingValues = paddingValues,
                    lazyListState = lazyListState,
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onDismissRequest = onDismissRequest,
                    onToggleFavoriteClicked = screenModel::removeFavorite,
                    onHideDuplicateClicked = screenModel::hideDuplicate,
                    loading = state.loading,
                )
            }
        }
    }
}
