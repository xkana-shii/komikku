plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.core.metro)

    implementation(libs.metro.runtime)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    implementation(kotlinx.datetime)

    implementation(libs.unifile)

    api(libs.sqldelight.android.paging)

    compileOnly(platform(compose.bom))
    compileOnly(compose.runtime.annotation)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
