// Variant of build.gradle.kts used when Android is disabled (-Pbible.android=false):
// identical except that the Android target and AGP plugin are left out. Keep the two files in sync.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "ComposeApp"; isStatic = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core-model"))
            implementation(project(":shared:feed-engine"))
            implementation(project(":shared:bible-data"))
            implementation(project(":shared:stories"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            // Story art: published images from the CDN, cached on the device (proposal §6.4).
            implementation(libs.coil.compose)
            implementation(libs.haze)
            implementation(libs.coil.network.ktor3)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

compose.resources {
    packageOfResClass = "app.bible.app.resources"
    publicResClass = false
}

compose.desktop {
    application {
        mainClass = "app.bible.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VerseUp"
            packageVersion = "1.0.0"
        }
    }
}
