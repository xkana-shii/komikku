package eu.kanade.tachiyomi.network.interceptor

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
// KNS <--
