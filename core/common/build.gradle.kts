plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
    alias(libs.plugins.metro)
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    // SY -->
    implementation(projects.i18nSy)
    // SY <--

    implementation(libs.metro.runtime)
    implementation(projects.core.metro)

    api(libs.logcat)

    api(libs.rxjava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    api(libs.apollo)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.libarchive)

    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.preferencektx)
    implementation(libs.androidx.webkit)

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // SY -->
    implementation(sylibs.xlog)
    implementation(libs.injekt)
    // SY <--
}
