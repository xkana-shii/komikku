package eu.kanade.domain.track.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

enum class AutoRereadState(val titleRes: StringResource) {
    ALWAYS(KMR.strings.auto_reread_always),
    ASK(KMR.strings.auto_reread_ask),
    NEVER(KMR.strings.auto_reread_never),
}
