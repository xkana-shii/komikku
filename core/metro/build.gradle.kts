plugins {
    id("mihon.library")

    alias(libs.plugins.metro)
}

android {
    namespace = "mihon.core.metro"
}

dependencies {
    implementation(libs.metro.runtime)
}
