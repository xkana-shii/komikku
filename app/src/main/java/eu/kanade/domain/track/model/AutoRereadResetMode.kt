package eu.kanade.domain.track.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class AutoRereadResetMode(val titleRes: StringResource) {
    RESET_TO_ZERO(KMR.strings.pref_auto_reread_reset_to_zero),
    RESET_TO_CURRENT_CHAPTER(KMR.strings.pref_auto_reread_reset_to_current_chapter),
}
