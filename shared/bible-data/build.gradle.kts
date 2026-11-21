plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        jvmTest.dependencies { implementation(libs.sqldelight.sqlite.driver) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        // Read-only, one file per translation (bible-<translation>.db). Built by :pipeline.
        create("BibleDatabase") {
            packageName.set("app.bible.data.bible")
            srcDirs("src/commonMain/sqldelight/bible")
        }
        // Read-only word-study pack (study.db): Strong's, STEP Bible and the KJV's word tags. Built by :pipeline.
        create("StudyDatabase") {
            packageName.set("app.bible.data.study")
            srcDirs("src/commonMain/sqldelight/study")
        }
        // Read-write, one per device: reading position, settings, highlights, bookmarks, notes.
        create("UserDatabase") {
            packageName.set("app.bible.data.user")
            srcDirs("src/commonMain/sqldelight/user")
        }
    }
}
