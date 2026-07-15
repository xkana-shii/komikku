plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "mihon.core.viewmodel"
}

dependencies {
    // KMK -->
    implementation(androidx.lifecycle.viewmodel)
    // KMK <--
}
