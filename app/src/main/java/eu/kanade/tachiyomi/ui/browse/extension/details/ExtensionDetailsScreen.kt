package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class ExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val viewModel = viewModel<ExtensionDetailsViewModel>(
            factory = ExtensionDetailsViewModel.Factory,
            extras = CreationExtras {
                set(ExtensionDetailsViewModel.PKG_NAME_KEY, pkgName)
            },
        )
        val state by viewModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        // KMK -->
        val source = state.extension?.sources?.getOrNull(0)
        // KMK <--

        ExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            // KMK -->
            onOpenWebView = if (source != null && source is HttpSource) {
                {
                    navigator.push(
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = source.name,
                            sourceId = source.id,
                        ),
                    )
                }
            } else {
                null
            },
            // KMK <--
            onClickEnableAll = { viewModel.toggleSources(true) },
            onClickDisableAll = { viewModel.toggleSources(false) },
            onClickClearCookies = viewModel::clearCookies,
            onClickUninstall = viewModel::uninstallExtension,
            onClickSource = viewModel::toggleSource,
            onClickIncognito = viewModel::toggleIncognito,
        )

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
