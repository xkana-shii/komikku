package eu.kanade.tachiyomi.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.webview.WebViewScreenContent
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.presentation.core.screens.LoadingScreen

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel =
            assistedMetroViewModel<WebViewViewModel, WebViewViewModel.Factory> { create(sourceId = sourceId) }

        val headers by viewModel.headers.collectAsState()
        if (headers == null) {
            LoadingScreen()
            return
        }

        WebViewScreenContent(
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = headers.orEmpty(),
            defaultUserAgentProvider = viewModel::defaultUserAgentProvider,
            onUrlChange = { assistUrl = it },
            onShare = { viewModel.shareWebpage(context, it) },
            onOpenInBrowser = { viewModel.openInBrowser(context, it) },
            onClearCookies = viewModel::clearCookies,
        )

        // KMK -->
        LaunchedEffect(Unit) {
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context, DiscordScreen.WEBVIEW) }
            }
        }
        // <-- KMK
    }
}
