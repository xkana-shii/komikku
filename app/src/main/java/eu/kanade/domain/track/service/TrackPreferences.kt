package eu.kanade.domain.track.service

import eu.kanade.domain.track.model.AutoRereadResetMode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.track.service.PreferredTrackerMap

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_username_${tracker.id}"),
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

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    val mangabakaScoreType: Preference<String> = preferenceStore.getString("mangabaka_score_type", MangaBaka.STEP_1)
    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun trackOnAddingToLibrary() = preferenceStore.getBoolean("track_on_adding_to_library", true)

    fun autoUpdateTrackOnMarkRead() = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )

    // Auto reread behavior preference
    fun autoRereadBehavior() = preferenceStore.getEnum(
        "pref_auto_reread_behavior",
        AutoTrackState.ASK,
    )

    // Auto reread reset mode preference
    fun autoRereadResetMode() = preferenceStore.getEnum(
        "pref_auto_reread_reset_mode",
        AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER,
    )

    // SY -->
    fun resolveUsingSourceMetadata() = preferenceStore.getBoolean(
        "pref_resolve_using_source_metadata_key",
        true,
    )
    // SY <--

    // KMK -->
    fun autoSyncProgressFromTrackers() = preferenceStore.getBoolean("pref_auto_sync_progress_from_trackers_key", true)
    fun preferredTrackerForManga() = preferenceStore.getString(Preference.appStateKey("pref_preferred_tracker_for_manga"), "")

    private val preferredTrackerLock = Any()
    fun getPreferredTrackerForManga(mangaId: Long): Long? = PreferredTrackerMap.decode(preferredTrackerForManga().get())[mangaId]

    fun setPreferredTrackerForManga(mangaId: Long, trackerId: Long?) = synchronized(preferredTrackerLock) {
        if (mangaId > 0) preferredTrackerForManga().set(PreferredTrackerMap.update(preferredTrackerForManga().get(), mangaId, trackerId))
    }

    fun priorityTrackerId() = preferenceStore.getLong("pref_priority_tracker_id", 0L)
    fun getPriorityTrackerId(): Long? = priorityTrackerId().get().takeIf { it > 0 }
    fun setPriorityTrackerId(trackerId: Long?) = priorityTrackerId().set(trackerId?.takeIf { it > 0 } ?: 0L)

    fun resolvePreferredTracker(mangaId: Long, applicable: Set<Long>): Long? =
        getPriorityTrackerId()?.takeIf { it in applicable }
    // KMK <--
}
