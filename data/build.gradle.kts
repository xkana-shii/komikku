plugins {
    id("mihon.library")
    kotlin("plugin.serialization")
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                generateAsync.set(true)
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
    implementation(projects.core.metro)

    implementation(libs.metro.runtime)

    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)

    implementation(kotlinx.datetime)

    api(libs.bundles.sqldelight)

    testImplementation(libs.bundles.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
