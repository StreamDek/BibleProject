rootProject.name = "bible-app"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Android needs the Android SDK and Google's Maven repository. Builds without them
// (for example a JVM-only CI job or a restricted sandbox) pass -Pbible.android=false:
// Android modules are skipped and the shared UI builds for desktop (+ iOS on macOS).
val withAndroid = providers.gradleProperty("bible.android").orNull != "false"

include(":shared:core-model")
include(":shared:feed-engine")
include(":shared:bible-data")
include(":shared:stories")
include(":composeApp")
include(":pipeline")
include(":backend")
if (withAndroid) {
    include(":androidApp")
} else {
    project(":composeApp").buildFileName = "build.noandroid.gradle.kts"
}
