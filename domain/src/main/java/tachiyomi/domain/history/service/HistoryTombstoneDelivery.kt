// KUN -->
package tachiyomi.domain.history.service

/**
 * Whether a history removal can reach a server right now.
 *
 * [DELIVERABLE] and [UNDELIVERABLE] are verdicts. [UNKNOWN] is the third answer a cached capability
 * probe forces: SyncYomi is selected, but its current host has not answered the v2 probe yet, so
 * nothing is known about it. The two readers need different answers for it —
 *
 * - a *removal* may not queue a tombstone on [UNKNOWN] (it is not confirmed that anything can send
 *   it, and the queue is only useful to a server that will);
 * - a *sweep* of already queued tombstones must not act on it: this run is about to ask, and
 *   dropping the queue before the answer would discard tombstones the answer would deliver.
 */
enum class HistoryTombstoneDeliverability {
    /** The current configuration can send a tombstone: SyncYomi v2, history sync on. */
    DELIVERABLE,

    /** Nothing can send it: another sync service is selected, or history sync is off. */
    UNDELIVERABLE,

    /** SyncYomi is selected but its current host has not been confirmed to speak v2 (yet). */
    UNKNOWN,
}

/**
 * Whether a local history removal can currently reach a server.
 *
 * Only the SyncYomi v2 deletions request consumes the queue (`history.pending_deletion`), so a
 * removal on a device whose sync is off, belongs to another service, or points at a host that does
 * not speak v2 has to reset the row *without* queueing it. A queued row nothing will ever send is
 * not merely useless: a restore payload skips it, "Clear database" keeps it, and a remote chapter
 * tombstone would be held back because the chapter still carries a history row.
 */
fun interface HistoryTombstoneDelivery {
    fun deliverability(): HistoryTombstoneDeliverability

    /**
     * The answer the removal paths need: only a [HistoryTombstoneDeliverability.DELIVERABLE] verdict
     * justifies a queue entry, so an unprobed host is treated as undeliverable here.
     */
    fun canDeliver(): Boolean = deliverability() == HistoryTombstoneDeliverability.DELIVERABLE
}
// KUN <--
