import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("mihon.benchmark")
    alias(libs.plugins.androidx.baselineProfile)
}

android {
    namespace = "mihon.baselineprofile"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions.managedDevices.allDevices {
        @Suppress("UnstableApiUsage")
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "google"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
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
