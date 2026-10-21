package tachiyomi.domain.track.service

// KNS -->
/**
 * Notified after tracker state was written to the database (never for a failed write), so automation
 * that depends on the tracker status (Smart Categories) acts on persisted state only.
 */
interface TrackPersistenceListener {
    suspend fun onTracksPersisted(mangaIds: Set<Long>)
}
// KNS <--
