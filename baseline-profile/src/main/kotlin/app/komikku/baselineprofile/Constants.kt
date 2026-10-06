package app.komikku.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry

// KMK -->
internal val TARGET_PACKAGE_NAME: String
    get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "targetAppId not passed as instrumentation runner argument"
    }
// KMK <--
