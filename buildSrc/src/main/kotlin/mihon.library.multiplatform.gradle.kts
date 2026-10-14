import mihon.buildlogic.configureKmpAndroidDefaults

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")

    id("mihon.code.lint")
}

configureKmpAndroidDefaults()
