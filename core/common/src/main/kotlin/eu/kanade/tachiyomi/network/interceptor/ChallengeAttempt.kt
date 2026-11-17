package eu.kanade.tachiyomi.network.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

// KNS -->
/**
 * One verification attempt for one request/host, as a single state machine. States only move forward and the first
 * terminal state wins, so a late WebView callback (a success after a cancel, say) can never override the outcome.
 * It holds no Android types, so it is exercised with plain unit tests.
 */
class ChallengeAttempt(
    val host: String,
    private val totalTimeoutNanos: Long = TOTAL_TIMEOUT_NANOS,
    private val automaticTimeoutNanos: Long = AUTOMATIC_TIMEOUT_NANOS,
) {
    enum class State(val terminal: Boolean) {
        Pending(false),
        Preparing(false),
        AutomaticVerification(false),

        /** Queued for, or showing, the dialog. */
        AwaitingUser(false),
        Succeeded(true),
        Cancelled(true),
        Failed(true),
        TimedOut(true),
    }

    private val monitor = Object()
    private val listeners = ArrayList<(State) -> Unit>(2)
    private val startNanos = System.nanoTime()

    @Volatile
    var state: State = State.Pending
        private set

    val isTerminal: Boolean get() = state.terminal

    private val _widget = MutableStateFlow<WidgetBounds?>(null)

    /** Where the verification widget sits on the challenge page, once the page reported it. */
    val widget: StateFlow<WidgetBounds?> = _widget

    fun updateWidget(bounds: WidgetBounds?) {
        if (!isTerminal) _widget.value = bounds
    }

    /** Moves to a later non-terminal state. False when the attempt is already terminal or would go backwards. */
    fun advance(to: State): Boolean {
        require(!to.terminal)
        synchronized(monitor) {
            if (state.terminal || to.ordinal <= state.ordinal) return false
            state = to
            monitor.notifyAll()
            return true
        }
    }

    /** Ends the attempt. Only the first call has an effect; returns whether this call was it. */
    fun finish(to: State): Boolean {
        require(to.terminal)
        val toNotify: List<(State) -> Unit>
        synchronized(monitor) {
            if (state.terminal) return false
            state = to
            toNotify = listeners.toList()
            listeners.clear()
            monitor.notifyAll()
        }
        toNotify.forEach { it(to) }
        return true
    }

    /** Runs [listener] once, when the attempt ends (right away if it already has). Runs outside any lock. */
    fun onTerminal(listener: (State) -> Unit) {
        val now: State?
        synchronized(monitor) {
            if (state.terminal) {
                now = state
            } else {
                listeners.add(listener)
                now = null
            }
        }
        now?.let(listener)
    }

    /**
     * Blocks until terminal. One monotonic deadline covers everything including time queued; before the user is
     * involved the shorter automatic limit applies. Interruption fails the attempt and restores the flag.
     */
    fun await(): State {
        try {
            synchronized(monitor) {
                while (true) {
                    val current = state
                    if (current.terminal) return current
                    val limit = if (current == State.AwaitingUser) {
                        totalTimeoutNanos
                    } else {
                        minOf(automaticTimeoutNanos, totalTimeoutNanos)
                    }
                    val remaining = startNanos + limit - System.nanoTime()
                    if (remaining <= 0) break
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            finish(State.Failed)
            return state
        }
        finish(State.TimedOut)
        return state
    }

    companion object {
        val TOTAL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(150)
        val AUTOMATIC_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30)
    }
}
/** Bounding box of the verification widget in CSS pixels, as reported by the challenge page. */
data class WidgetBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        const val EVENT_PREFIX = "widget:"
        private const val MIN_SIZE = 20f
        private const val MAX_SIZE = 4000f

        /** Parses `widget:left,top,width,height`; null for anything malformed or implausibly sized. */
        fun parse(event: String): WidgetBounds? {
            if (!event.startsWith(EVENT_PREFIX)) return null
            val parts = event.removePrefix(EVENT_PREFIX).split(',').map { it.trim().toFloatOrNull() ?: return null }
            if (parts.size != 4 || parts.any { it.isNaN() || it.isInfinite() }) return null
            val (left, top, width, height) = parts
            if (width < MIN_SIZE || height < MIN_SIZE || width > MAX_SIZE || height > MAX_SIZE) return null
            return WidgetBounds(left, top, width, height)
        }
    }
}
// KNS <--
