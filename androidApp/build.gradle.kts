plugins {
    // Versions come from the root buildscript classpath (see the root build.gradle.kts).
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.bible.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.bible"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        // Every debug build, local or CI, is signed with this one committed debug key. Each CI runner used to make
        // its own, so a new APK could not install over the last and Android made you uninstall it first, which
        // deletes notes, highlights, bookmarks and reading progress. Not a secret: debug builds only.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}
