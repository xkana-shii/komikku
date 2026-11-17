package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.interceptor.ChallengeAttempt.State
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// KNS -->
private class FakeChallenge(
    override val host: String,
    override val attempt: ChallengeAttempt = ChallengeAttempt(host),
) : QueuedChallenge {
    var activations = 0
    override fun onActivated() {
        activations++
    }
}

class ChallengeAttemptTest {

    private fun fast(host: String = "a.test") = ChallengeAttempt(host, 400_000_000L, 100_000_000L)

    @Test
    fun `first terminal state wins`() {
        val attempt = fast()
        attempt.finish(State.Cancelled) shouldBe true
        attempt.finish(State.Succeeded) shouldBe false
        attempt.finish(State.Failed) shouldBe false
        attempt.state shouldBe State.Cancelled
    }

    @Test
    fun `late success after cancel cannot override it and repeat cancel is safe`() {
        val attempt = fast()
        attempt.advance(State.AwaitingUser)
        attempt.finish(State.Cancelled)
        attempt.finish(State.Cancelled) shouldBe false
        attempt.finish(State.Succeeded) shouldBe false
        attempt.advance(State.AwaitingUser) shouldBe false
        attempt.await() shouldBe State.Cancelled
    }

    @Test
    fun `states only move forward`() {
        val attempt = fast()
        attempt.advance(State.AutomaticVerification) shouldBe true
        attempt.advance(State.Preparing) shouldBe false
        attempt.advance(State.AutomaticVerification) shouldBe false
        attempt.advance(State.AwaitingUser) shouldBe true
    }

    @Test
    fun `success ends a waiting request`() {
        val attempt = ChallengeAttempt("a.test")
        attempt.advance(State.AwaitingUser)
        thread {
            Thread.sleep(30)
            attempt.finish(State.Succeeded)
        }
        attempt.await() shouldBe State.Succeeded
    }

    @Test
    fun `automatic phase times out sooner than the total deadline`() {
        val attempt = fast()
        val start = System.nanoTime()
        attempt.await() shouldBe State.TimedOut
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        (elapsedMs < 300) shouldBe true
    }

    @Test
    fun `waiting for the user ends at the single total deadline`() {
        val attempt = fast()
        attempt.advance(State.AwaitingUser)
        val start = System.nanoTime()
        attempt.await() shouldBe State.TimedOut
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        (elapsedMs in 300..900) shouldBe true
    }

    @Test
    fun `interrupt fails the attempt and keeps the interrupt flag`() {
        val attempt = ChallengeAttempt("a.test")
        var result: State? = null
        var flag = false
        val worker = thread {
            result = attempt.await()
            flag = Thread.currentThread().isInterrupted
        }
        Thread.sleep(50)
        worker.interrupt()
        worker.join(2000)
        result shouldBe State.Failed
        flag shouldBe true
    }

    @Test
    fun `terminal listeners run once, also when added late`() {
        val attempt = fast()
        var count = 0
        attempt.onTerminal { count++ }
        attempt.finish(State.Failed)
        attempt.finish(State.Failed)
        attempt.onTerminal { count++ }
        count shouldBe 2
    }

    @Test
    fun `cancel unblocks a waiting thread promptly`() {
        val attempt = ChallengeAttempt("a.test")
        attempt.advance(State.AwaitingUser)
        val done = CountDownLatch(1)
        thread {
            attempt.await()
            done.countDown()
        }
        attempt.finish(State.Cancelled)
        (done.await(1, TimeUnit.SECONDS)) shouldBe true
    }
}

class ChallengeQueueTest {

    @Test
    fun `first challenge is active and later ones wait in order`() {
        val queue = ChallengeQueue()
        val a = FakeChallenge("a.test")
        val b = FakeChallenge("b.test")
        val c = FakeChallenge("c.test")
        queue.enqueue(a) shouldBe true
        queue.enqueue(b) shouldBe true
        queue.enqueue(c) shouldBe true
        queue.active.value shouldBe a
        a.activations shouldBe 1
        b.activations shouldBe 0
    }

    @Test
    fun `a new challenge never replaces the active one`() {
        val queue = ChallengeQueue()
        val a = FakeChallenge("a.test")
        queue.enqueue(a)
        queue.enqueue(FakeChallenge("b.test"))
        queue.active.value shouldBe a
    }

    @Test
    fun `finishing the active one advances in FIFO order`() {
        val queue = ChallengeQueue()
        val a = FakeChallenge("a.test")
        val b = FakeChallenge("b.test")
        val c = FakeChallenge("c.test")
        listOf(a, b, c).forEach(queue::enqueue)
        a.attempt.finish(State.Succeeded)
        queue.active.value shouldBe b
        b.activations shouldBe 1
        b.attempt.finish(State.Cancelled)
        queue.active.value shouldBe c
        c.attempt.finish(State.TimedOut)
        queue.active.value shouldBe null
        queue.size shouldBe 0
    }

    @Test
    fun `cancelling a queued challenge removes it without touching the active one`() {
        val queue = ChallengeQueue()
        val a = FakeChallenge("a.test")
        val b = FakeChallenge("b.test")
        val c = FakeChallenge("c.test")
        listOf(a, b, c).forEach(queue::enqueue)
        b.attempt.finish(State.Cancelled)
        queue.active.value shouldBe a
        queue.size shouldBe 2
        a.attempt.finish(State.Succeeded)
        queue.active.value shouldBe c
    }

    @Test
    fun `same host is not queued twice and ended attempts are refused`() {
        val queue = ChallengeQueue()
        queue.enqueue(FakeChallenge("a.test")) shouldBe true
        queue.enqueue(FakeChallenge("a.test")) shouldBe false
        val dead = FakeChallenge("b.test")
        dead.attempt.finish(State.Failed)
        queue.enqueue(dead) shouldBe false
    }

    @Test
    fun `queue is bounded`() {
        val queue = ChallengeQueue(maxSize = 2)
        queue.enqueue(FakeChallenge("a.test")) shouldBe true
        queue.enqueue(FakeChallenge("b.test")) shouldBe true
        queue.enqueue(FakeChallenge("c.test")) shouldBe false
    }

    @Test
    fun `a challenge that ended while queued is skipped`() {
        val queue = ChallengeQueue()
        val a = FakeChallenge("a.test")
        val b = FakeChallenge("b.test")
        val c = FakeChallenge("c.test")
        listOf(a, b, c).forEach(queue::enqueue)
        b.attempt.finish(State.TimedOut)
        a.attempt.finish(State.Succeeded)
        queue.active.value shouldBe c
    }

    @Test
    fun `concurrent hosts all get through exactly once`() {
        val queue = ChallengeQueue()
        val challenges = (1..8).map { FakeChallenge("h$it.test") }
        val threads = challenges.map { c -> thread { queue.enqueue(c) } }
        threads.forEach { it.join() }
        var seen = 0
        while (true) {
            val active = queue.active.value ?: break
            seen++
            active.attempt.finish(State.Succeeded)
        }
        seen shouldBe 8
        challenges.all { it.activations == 1 } shouldBe true
    }
}

class HostFailureLogTest {

    @Test
    fun `a failure after a request started waiting is shared with it`() {
        val log = HostFailureLog()
        val waitingSince = System.nanoTime()
        log.failedSince("a.test", waitingSince) shouldBe false
        log.record("a.test")
        log.failedSince("a.test", waitingSince) shouldBe true
        log.failedSince("b.test", waitingSince) shouldBe false
    }

    @Test
    fun `an older failure does not block a later request`() {
        val log = HostFailureLog()
        log.record("a.test")
        Thread.sleep(2)
        log.failedSince("a.test", System.nanoTime()) shouldBe false
    }
}

class CloudflareHostOwnershipTest {

    @Test
    fun `an activity switch does not count as background and the latest host owns the dialog`() {
        val main = Any()
        val reader = Any()
        CloudflareChallengeCoordinator.onHostResumed(main)
        CloudflareChallengeCoordinator.canPrompt shouldBe true
        CloudflareChallengeCoordinator.onHostPaused(main)
        // between pause and the next resume
        CloudflareChallengeCoordinator.canPrompt shouldBe true
        CloudflareChallengeCoordinator.onHostResumed(reader)
        CloudflareChallengeCoordinator.activeHost.value shouldBe reader
        CloudflareChallengeCoordinator.onHostPaused(reader)
        CloudflareChallengeCoordinator.activeHost.value shouldBe null
    }

    @Test
    fun `rapid pause and resume leaves one owner`() {
        val host = Any()
        repeat(5) {
            CloudflareChallengeCoordinator.onHostResumed(host)
            CloudflareChallengeCoordinator.onHostPaused(host)
        }
        CloudflareChallengeCoordinator.onHostResumed(host)
        CloudflareChallengeCoordinator.activeHost.value shouldBe host
        CloudflareChallengeCoordinator.onHostPaused(host)
        CloudflareChallengeCoordinator.activeHost.value shouldBe null
    }
}
// KNS <--

// KNS -->
class WidgetBoundsTest {

    @Test
    fun `parses a reported widget box`() {
        WidgetBounds.parse("widget:12,340,300,65") shouldBe WidgetBounds(12f, 340f, 300f, 65f)
    }

    @Test
    fun `rejects malformed or implausible reports`() {
        WidgetBounds.parse("interactiveBegin") shouldBe null
        WidgetBounds.parse("widget:1,2,3") shouldBe null
        WidgetBounds.parse("widget:a,b,c,d") shouldBe null
        WidgetBounds.parse("widget:0,0,5,5") shouldBe null
        WidgetBounds.parse("widget:0,0,99999,50") shouldBe null
        WidgetBounds.parse("widget:NaN,0,300,65") shouldBe null
    }

    @Test
    fun `the widget is kept per attempt and ignored once it ended`() {
        val attempt = ChallengeAttempt("a.test")
        attempt.updateWidget(WidgetBounds(0f, 10f, 300f, 65f))
        attempt.widget.value shouldBe WidgetBounds(0f, 10f, 300f, 65f)
        attempt.finish(ChallengeAttempt.State.Cancelled)
        attempt.updateWidget(WidgetBounds(0f, 99f, 300f, 65f))
        attempt.widget.value shouldBe WidgetBounds(0f, 10f, 300f, 65f)
    }
}
// KNS <--
