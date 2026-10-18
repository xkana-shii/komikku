pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val regex = "com.android.(library|application)".toRegex()
            if (regex matches requested.id.id) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinx") {
            from(files("gradle/kotlinx.versions.toml"))
        }
        create("androidx") {
            from(files("gradle/androidx.versions.toml"))
        }
        create("compose") {
            from(files("gradle/compose.versions.toml"))
        }
        create("sylibs") {
            from(files("gradle/sy.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// KMK -->
gradle.beforeProject {
    if (path in setOf(":i18n", ":i18n-kmk", ":i18n-sy")) {
        extra["kotlin.native.cocoapods.archs"] = ""
        extra["kotlin.native.cocoapods.configuration"] = ""
        extra["kotlin.native.cocoapods.platform"] = ""
        extra["moko.resources.ARCHS"] = ""
        extra["moko.resources.CONFIGURATION"] = ""
        extra["moko.resources.PLATFORM_NAME"] = ""
        extra["moko.resources.strictLineBreaks"] = "false"
    }
}
// KMK <--

rootProject.name = "Komikku"
include(":app")
include(":core-metadata")
include(":core:archive")
include(":core:common")
include(":core:metro")
include(":data")
include(":domain")
include(":i18n")
// KMK -->
include(":i18n-kmk")
include(":flagkit")
// KMK <--
// SY -->
include(":i18n-sy")
// SY <--
include(":icons:custom-material-symbols")
include(":icons:material-symbols")
include(":icons:simple-icons")
// KMK -->
include(":baseline-profile")
// KMK <--
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
// KMK --> Provides no-op or opt-in telemetry APIs required by the integrated app graph.
include(":telemetry")
// KMK <--
