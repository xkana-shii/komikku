package mihon.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the shared Android target configuration to a Kotlin Multiplatform Android library,
 * mirroring mihon/main's `PluginKotlinMultiplatform`.
 */
fun Project.configureKmpAndroidDefaults() {
    extensions.configure<KotlinMultiplatformExtension> {
        targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
            minSdk = AndroidConfig.MIN_SDK
            compileSdk {
                version = release(AndroidConfig.COMPILE_SDK)
            }
            enableCoreLibraryDesugaring = true
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.desugar)
    }
}

/**
 * Configures the KMP Android library target, mirroring mihon/main's `kotlin { android { } }`.
 */
fun KotlinMultiplatformExtension.android(block: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach(block)
}
