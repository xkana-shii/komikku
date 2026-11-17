package eu.kanade.tachiyomi.network.interceptor

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

// KNS -->
/**
 * Bridges [CloudflareInterceptor] and the UI. Challenges that need a human go through a fair [ChallengeQueue]; the
 * screen currently owning the foreground ([activeHost]) shows the active one. A challenge lives here, not in an
 * activity, so it survives MainActivity <-> ReaderActivity switches and rotations.
 */
object CloudflareChallengeCoordinator {

    class Session(
        override val host: String,
        override val attempt: ChallengeAttempt,
        val webView: WebView,
        private val runOnMain: (() -> Unit) -> Unit,
    ) : QueuedChallenge {
        fun cancel() {
            attempt.finish(ChallengeAttempt.State.Cancelled)
        }

        /** Reloads the challenge page, only while this session is still live. */
        fun retry() {
            runOnMain { if (!attempt.isTerminal) webView.reload() }
        }

        // A queued WebView sits paused until it is shown
        fun pause() = runOnMain { if (!attempt.isTerminal) webView.onPause() }

        override fun onActivated() = runOnMain { if (!attempt.isTerminal) webView.onResume() }
    }

    internal val queue = ChallengeQueue()
    val session: StateFlow<QueuedChallenge?> get() = queue.active

    private val hostsLock = Any()
    private val resumedHosts = LinkedHashSet<Any>()
    private val _activeHost = MutableStateFlow<Any?>(null)

    /** The screen that should show the dialog: the most recently resumed one. */
    val activeHost: StateFlow<Any?> = _activeHost

    private var lastPauseNanos = Long.MIN_VALUE

    /**
     * True while a screen that can show the dialog is resumed, or one just paused (an activity switch pauses before
     * the next resumes, which must not count as the background).
     */
    val canPrompt: Boolean
        get() = synchronized(hostsLock) {
            resumedHosts.isNotEmpty() ||
                (lastPauseNanos != Long.MIN_VALUE && System.nanoTime() - lastPauseNanos < GRACE_NANOS)
        }

    fun onHostResumed(host: Any) {
        synchronized(hostsLock) {
            resumedHosts.remove(host)
            resumedHosts.add(host)
            _activeHost.value = host
        }
    }

    fun onHostPaused(host: Any) {
        synchronized(hostsLock) {
            if (resumedHosts.remove(host)) lastPauseNanos = System.nanoTime()
            _activeHost.value = resumedHosts.lastOrNull()
        }
    }

    internal fun enqueue(session: Session): Boolean {
        val queued = queue.enqueue(session)
        // Waiting behind another dialog: don't keep its page running
        if (queued && queue.active.value !== session) session.pause()
        return queued
    }

    private const val GRACE_NANOS = 1_500_000_000L
}

/**
 * Remembers when a host's verification last ended without success, so requests that were waiting on that host fail
 * with it instead of each opening a new dialog right after the user cancelled.
 */
internal class HostFailureLog {
    private val last = ConcurrentHashMap<String, Long>()

    fun record(host: String) {
        last[host] = System.nanoTime()
    }

    fun failedSince(host: String, sinceNanos: Long): Boolean {
        val at = last[host] ?: return false
        return at - sinceNanos >= 0
    }
}
// KNS <--
