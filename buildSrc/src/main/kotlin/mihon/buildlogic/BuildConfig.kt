package mihon.buildlogic

import org.gradle.api.Project

interface BuildConfig {
    // KMK --> Optional telemetry remains disabled unless explicitly requested.
    val includeTelemetry: Boolean
    // KMK <--
    val enableUpdater: Boolean
    val enableCodeShrink: Boolean
    val includeDependencyInfo: Boolean
}

val Project.Config: BuildConfig get() = object : BuildConfig {
    // KMK --> Keep telemetry opt-in for Komikku's existing build variants.
    override val includeTelemetry: Boolean = providers.gradleProperty("include-telemetry").isPresent
    // KMK <--
    override val enableUpdater: Boolean = providers.gradleProperty("enable-updater").isPresent
    override val enableCodeShrink: Boolean = !providers.gradleProperty("disable-code-shrink").isPresent
    override val includeDependencyInfo: Boolean = providers.gradleProperty("include-dependency-info").isPresent
}
