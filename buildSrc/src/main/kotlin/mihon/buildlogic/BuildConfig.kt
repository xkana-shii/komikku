package mihon.buildlogic

import org.gradle.api.Project

interface BuildConfig {
    val enableUpdater: Boolean
    val enableCodeShrink: Boolean
    val includeDependencyInfo: Boolean
}

val Project.Config: BuildConfig get() = object : BuildConfig {
    override val enableUpdater: Boolean = providers.gradleProperty("enable-updater").isPresent
    override val enableCodeShrink: Boolean = !providers.gradleProperty("disable-code-shrink").isPresent
    override val includeDependencyInfo: Boolean = providers.gradleProperty("include-dependency-info").isPresent
}
