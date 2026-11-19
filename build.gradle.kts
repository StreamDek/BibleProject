buildscript {
    // The Android Gradle Plugin is only put on the classpath when Android is enabled
    // (see settings.gradle.kts). Loading it here, in the root, keeps a single classloader
    // for the Kotlin and Android plugins across all modules.
    if (providers.gradleProperty("bible.android").orNull != "false") {
        repositories { google(); mavenCentral() }
        dependencies {
            classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        }
    }
}

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktor) apply false
}
