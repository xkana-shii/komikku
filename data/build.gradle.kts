plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        // KMK -->
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // KMK <--
    }

    // KMK -->
    sourceSets {
        getByName("test").java.srcDir("src/testSupport/kotlin")
        getByName("androidTest").java.srcDir("src/testSupport/kotlin")
    }
    // KMK <--

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                // KMK -->
                generateAsync.set(true)
                // KMK <--
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)

    api(libs.bundles.sqldelight)

    // KMK -->
    testImplementation(libs.bundles.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(androidx.test.ext)
    androidTestImplementation(androidx.test.runner)
    androidTestImplementation(libs.sqlite.bundled)
    androidTestImplementation(sylibs.sqlcipher)
    // KMK <--
}
