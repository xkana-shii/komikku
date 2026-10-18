import mihon.buildlogic.Config

plugins {
    id("mihon.library")
}

android {
    namespace = "mihon.telemetry"

    sourceSets {
        getByName("main") {
            // KMK --> Compile a no-op facade unless the explicit telemetry build flag is present.
            if (Config.includeTelemetry) {
                kotlin.directories.add("src/firebase/kotlin")
            } else {
                kotlin.directories.add("src/noop/kotlin")
                manifest.srcFile("src/noop/AndroidManifest.xml")
            }
            // KMK <--
        }
    }
}

dependencies {
    // KMK --> Telemetry dependencies are opt-in along with the implementation source set.
    if (Config.includeTelemetry) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
    // KMK <--

    implementation(sylibs.xlog)
}
