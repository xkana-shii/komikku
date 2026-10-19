@file:Suppress("UNUSED", "KotlinConstantConditions")

package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig

val telemetryIncluded: Boolean
    inline get() = BuildConfig.TELEMETRY_INCLUDED

val isDebugBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "debug"

// KUN -->
// Debug builds are installed by the developer, not through the updater. Keeping the updater
// out of them also avoids update prompts/downloads fighting with a local install.
val updaterEnabled: Boolean
    inline get() = BuildConfig.UPDATER_ENABLED && !isDebugBuildType
// KUN <--

val isNightlyBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "nightly"

val isReleaseBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "release"

val isFossBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "foss"

val isBenchmarkBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE.contains("nonMinified") || BuildConfig.BUILD_TYPE.contains("benchmark")
