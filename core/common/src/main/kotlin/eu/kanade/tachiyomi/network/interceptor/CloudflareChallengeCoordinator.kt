package eu.kanade.tachiyomi.network.interceptor

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

// KNS -->
/**
 * Bridges [CloudflareInterceptor] and the UI: while an activity able to show the verification dialog is resumed,
 * an interactive challenge is handed to it instead of failing. Only one session exists at a time, because the
 * interceptor solves a host's challenge under that host's lock.
 */
object CloudflareChallengeCoordinator {

    class Session(
        val host: String,
        val webView: WebView,
        internal val onCancel: () -> Unit,
    ) {
        fun cancel() = onCancel()
        fun retry() = webView.reload()
    }

    private val resumedHosts = AtomicInteger(0)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    /** True while the app is in the foreground with a screen that can show the dialog. */
    val canPrompt: Boolean get() = resumedHosts.get() > 0

    fun onHostResumed() {
        resumedHosts.incrementAndGet()
    }

    fun onHostPaused() {
        resumedHosts.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    internal fun show(session: Session) {
        _session.value = session
    }

    internal fun dismiss(session: Session) {
        _session.compareAndSet(session, null)
    }
}
// KNS <--
