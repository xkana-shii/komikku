// KUN -->
package tachiyomi.domain.history.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.service.HistoryTombstoneDeliverability
import tachiyomi.domain.history.service.HistoryTombstoneDelivery

/**
 * The user's removal paths have to queue the rows, not only reset them: a reset alone is invisible
 * to the SyncYomi deletions request, which reads `history.pending_deletion`, so the removal would
 * never reach the server and the next restore would bring the entries back.
 *
 * The queue flag is only written when a tombstone can actually be delivered
 * ([HistoryTombstoneDelivery]). A queued row nothing will send is not harmless: a restore payload
 * skips it, "clear database" keeps it, and it pins its chapter against a remote deletion.
 */
class RemoveHistoryTest {

    private val repository = mockk<HistoryRepository>(relaxed = true)

    @Test
    fun `removing a selection queues exactly the selected rows`() = runTest {
        removeHistory(canDeliver = true).await(listOf(11L, 12L))

        coVerify(exactly = 1) { repository.removeHistory(listOf(11L, 12L), queueForSync = true) }
        // a plain reset leaves nothing for the deletions request to send
        coVerify(exactly = 0) { repository.removeHistory(any(), queueForSync = false) }
        coVerify(exactly = 0) { repository.resetHistory(any()) }
    }

    @Test
    fun `removing a manga from history queues every row of it`() = runTest {
        removeHistory(canDeliver = true).awaitManga(listOf(7L, 8L))

        coVerify(exactly = 1) { repository.removeHistoryByMangaIds(listOf(7L, 8L), queueForSync = true) }
        coVerify(exactly = 0) { repository.resetHistoryByMangaIds(any()) }
    }

    @Test
    fun `a removal that no sync can deliver only resets the rows`() = runTest {
        val removeHistory = removeHistory(canDeliver = false)

        removeHistory.await(listOf(11L))
        removeHistory.awaitManga(listOf(7L))
        removeHistory.awaitAll()

        // nothing may be marked pending: no request would ever send it, and the marker blocks the
        // row from being restored over, cleaned up or dropped with a remote chapter deletion
        coVerify(exactly = 1) { repository.removeHistory(listOf(11L), queueForSync = false) }
        coVerify(exactly = 1) { repository.removeHistoryByMangaIds(listOf(7L), queueForSync = false) }
        coVerify(exactly = 1) { repository.removeAllHistory(queueForSync = false) }
        coVerify(exactly = 0) { repository.removeHistory(any(), queueForSync = true) }
        coVerify(exactly = 0) { repository.removeHistoryByMangaIds(any(), queueForSync = true) }
        coVerify(exactly = 0) { repository.removeAllHistory(queueForSync = true) }
    }

    @Test
    fun `clearing all history queues every row when a tombstone can be delivered`() = runTest {
        removeHistory(canDeliver = true).awaitAll()

        coVerify(exactly = 1) { repository.removeAllHistory(queueForSync = true) }
    }

    @Test
    fun `clearing all history reports whether the rows were removed`() = runTest {
        // A hardcoded result in the interactor would pass a test that only checks one answer.
        coEvery { repository.removeAllHistory(any()) } returns false
        assertFalse(removeHistory(canDeliver = true).awaitAll())

        coEvery { repository.removeAllHistory(any()) } returns true
        assertTrue(removeHistory(canDeliver = true).awaitAll())
    }

    @Test
    fun `an unprobed host does not queue a tombstone`() = runTest {
        val removeHistory = RemoveHistory(
            repository = repository,
            tombstoneDelivery = HistoryTombstoneDelivery { HistoryTombstoneDeliverability.UNKNOWN },
        )

        removeHistory.await(listOf(11L))

        // UNKNOWN is not a confirmation that a request will read the marker, so the removal is only
        // a reset; the sweep of already queued markers is the one decision that must not act on it
        coVerify(exactly = 1) { repository.removeHistory(listOf(11L), queueForSync = false) }
        coVerify(exactly = 0) { repository.removeHistory(any(), queueForSync = true) }
    }

    /** The production seam is a `fun interface`, so the test drives the real policy shape. */
    private fun removeHistory(canDeliver: Boolean) = RemoveHistory(
        repository = repository,
        tombstoneDelivery = HistoryTombstoneDelivery {
            if (canDeliver) {
                HistoryTombstoneDeliverability.DELIVERABLE
            } else {
                HistoryTombstoneDeliverability.UNDELIVERABLE
            }
        },
    )
}
// KUN <--
