import com.android.build.api.dsl.ManagedVirtualDevice

// KMK -->
plugins {
    id("mihon.benchmark")
    alias(libs.plugins.androidx.baselineProfile)
}

android {
    namespace = "app.komikku.baselineprofile"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "google"
        }
    }
}

baselineProfile {
    useConnectedDevices = providers.gradleProperty("baselineProfile.useConnectedDevices")
        .map(String::toBoolean).getOrElse(false)
    if (!useConnectedDevices) managedDevices += "pixel6Api34"
}

dependencies {
    implementation(androidx.benchmark.macro)
    implementation(androidx.test.ext)
    implementation(androidx.test.espresso.core)
    implementation(androidx.test.uiautomator)
}

androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        val applicationId = variant.testedApks.map { artifactsLoader.load(it)?.applicationId }
        variant.instrumentationRunnerArguments.put("targetAppId", applicationId)
    }
}
// KMK <--
