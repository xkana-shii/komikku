package eu.kanade.domain.track.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.track.service.PreferredTrackerMap

@Inject
@SingleIn(AppScope::class)
class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
        "",
    )

    fun trackDisplayUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_displayname_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_password_${tracker.id}"),
        "",
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) = preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), "")

    val anilistScoreType = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    val kitsuScoreType: Preference<String> = preferenceStore.getString("kitsu_score_type", Kitsu.RATING_ADVANCED)

    val mangabakaScoreType: Preference<String> = preferenceStore.getString("mangabaka_score_type", MangaBaka.STEP_1)

    val autoUpdateTrack = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    val trackOnAddingToLibrary = preferenceStore.getBoolean("track_on_adding_to_library", true)

    val autoUpdateTrackOnMarkRead = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    // SY -->
    val resolveUsingSourceMetadata = preferenceStore.getBoolean(
        "pref_resolve_using_source_metadata_key",
        true,
    )
    // SY <--

    // KMK -->
    val autoSyncProgressFromTrackers = preferenceStore.getBoolean("pref_auto_sync_progress_from_trackers_key", true)
    // KMK <--

    // KNS -->
    /** Preferred tracker per manga ([PreferredTrackerMap] JSON); device state, like the previous fork. */
    val preferredTrackerForManga = preferenceStore.getString(
        Preference.appStateKey("pref_preferred_tracker_for_manga"),
        "",
    )

    /** The tracker preferred when a manga has no preference of its own; 0 = none. */
    val priorityTrackerId = preferenceStore.getLong("pref_priority_tracker_id", 0L)

    private val preferredTrackerLock = Any()

    fun getPreferredTrackerForManga(mangaId: Long): Long? =
        PreferredTrackerMap.decode(preferredTrackerForManga.get())[mangaId]

    fun setPreferredTrackerForManga(mangaId: Long, trackerId: Long?) = synchronized(preferredTrackerLock) {
        if (mangaId > 0) {
            preferredTrackerForManga.set(PreferredTrackerMap.update(preferredTrackerForManga.get(), mangaId, trackerId))
        }
    }

    /** The manga's preferred tracker, else the global priority tracker, if it is among [applicable]. */
    fun resolvePreferredTracker(mangaId: Long, applicable: Set<Long>): Long? =
        getPreferredTrackerForManga(mangaId)?.takeIf { it in applicable }
            ?: priorityTrackerId.get().takeIf { it > 0 && it in applicable }
    // KNS <--
}
