plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    api(kotlinx.serialization.json)
    api(libs.injekt)
    api(libs.rxjava)
    api(libs.jsoup)

    // SY -->
    api(projects.i18n)
    api(projects.i18nSy)
    api(kotlinx.reflect)
    // SY <--

    implementation(project.dependencies.platform(compose.bom))
    implementation(compose.runtime)

    implementation(projects.core.common)
    api(libs.preferencektx)

    // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
    implementation(kotlinx.coroutines.android)
    implementation(project.dependencies.platform(kotlinx.coroutines.bom))
}
