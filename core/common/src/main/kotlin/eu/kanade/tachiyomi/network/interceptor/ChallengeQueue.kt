package eu.kanade.tachiyomi.network.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// KNS -->
/** A challenge waiting for, or occupying, the one dialog. */
interface QueuedChallenge {
    val host: String
    val attempt: ChallengeAttempt

    /** The challenge became the shown one. Called outside the queue's lock. */
    fun onActivated()
}

/**
 * Fair FIFO of challenges that need the user. Only the head is active (shown); the others wait their turn and are
 * never replaced. A challenge leaves the queue when its attempt ends, whatever the outcome, and the next one
 * activates. Normal traffic never touches this.
 */
class ChallengeQueue(private val maxSize: Int = MAX_SIZE) {
    private val lock = Any()
    private val items = ArrayDeque<QueuedChallenge>()
    private val _active = MutableStateFlow<QueuedChallenge?>(null)
    val active: StateFlow<QueuedChallenge?> = _active

    val size: Int get() = synchronized(lock) { items.size }

    /** False when the queue is full, the host already has a challenge queued, or the attempt already ended. */
    fun enqueue(challenge: QueuedChallenge): Boolean {
        var activated: QueuedChallenge? = null
        synchronized(lock) {
            if (challenge.attempt.isTerminal || items.size >= maxSize || items.any { it.host == challenge.host }) {
                return false
            }
            items.addLast(challenge)
            if (_active.value == null) activated = promoteLocked()
        }
        challenge.attempt.onTerminal { remove(challenge) }
        activated?.onActivated()
        return true
    }

    fun remove(challenge: QueuedChallenge) {
        var activated: QueuedChallenge? = null
        synchronized(lock) {
            if (!items.remove(challenge)) return
            if (_active.value === challenge) {
                _active.value = null
                activated = promoteLocked()
            }
        }
        activated?.onActivated()
    }

    // Skips challenges that ended while waiting
    private fun promoteLocked(): QueuedChallenge? {
        while (items.isNotEmpty()) {
            val head = items.first()
            if (!head.attempt.isTerminal) {
                _active.value = head
                return head
            }
            items.removeFirst()
        }
        return null
    }

    companion object {
        const val MAX_SIZE = 8
    }
}
// KNS <--
