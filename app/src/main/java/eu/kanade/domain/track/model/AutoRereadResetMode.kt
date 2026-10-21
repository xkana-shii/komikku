package eu.kanade.domain.track.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

// KNS -->
/** What starting a reread resets (same values as the previous personal fork). */
enum class AutoRereadResetMode(val titleRes: StringResource) {
    /** Every chapter becomes unread and the trackers start from 0. */
    RESET_TO_ZERO(KMR.strings.pref_auto_reread_reset_to_zero),

    /** Chapters after the current one become unread and the trackers continue from the current chapter. */
    RESET_TO_CURRENT_CHAPTER(KMR.strings.pref_auto_reread_reset_to_current_chapter),
}
// KNS <--
