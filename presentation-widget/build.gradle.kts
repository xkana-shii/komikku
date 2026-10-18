plugins {
    id("mihon.library")
    id("mihon.library.compose")
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.presentation.widget"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    api(projects.i18n)

    implementation(compose.glance)
    implementation(libs.material)

    implementation(platform(libs.coil.bom))

    implementation(kotlinx.datetime)

    implementation(libs.coil.core)

    api(libs.injekt)

    implementation(libs.metro.runtime)
    implementation(projects.core.metro)
}
