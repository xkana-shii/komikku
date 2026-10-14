plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidx.gradle)
    // KUN -->
    // sdk-common (which provides Svg2Vector) is resolved from AGP's own dependency
    // metadata so its version always matches the AGP version in use.
    implementation(androidx.gradle.runtimeDependency("sdk-common"))
    // KUN <--
    implementation(kotlinx.gradle)
    implementation(kotlinx.compose.compiler.gradle)
    implementation(libs.spotless.gradle)
    implementation(gradleApi())

    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(compose.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}

// KUN -->
fun Provider<MinimalExternalModuleDependency>.runtimeDependency(name: String): Provider<String> = map { dependency ->
    configurations.detachedConfiguration(dependencies.create(dependency))
        .attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
        .incoming.resolutionResult.allComponents
        .firstOrNull { it.moduleVersion?.name == name }
        ?.moduleVersion
        ?.toString()
        ?: error("${dependency.module} has no runtime dependency named $name")
}
// KUN <--
