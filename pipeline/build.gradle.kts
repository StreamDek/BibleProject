plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java { targetCompatibility = JavaVersion.VERSION_17; sourceCompatibility = JavaVersion.VERSION_17 }

dependencies {
    implementation(project(":shared:core-model"))
    implementation(project(":shared:bible-data"))
    implementation(project(":shared:feed-engine"))
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqlite.jdbc)
    testImplementation(kotlin("test"))
}

application { mainClass.set("app.bible.pipeline.MainKt") }

val repoRoot = rootProject.layout.projectDirectory

// ./gradlew :pipeline:importKjv  →  builds the bundled KJV database and the grouper test fixture.
tasks.register<JavaExec>("importKjv") {
    group = "content"
    description = "Imports the KJV OSIS source into bible-kjv.db"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bible.pipeline.MainKt")
    args(
        "import-kjv",
        repoRoot.file("content/sources/kjv/eng-kjv.osis.xml").asFile.path,
        repoRoot.file("composeApp/src/commonMain/composeResources/files/bible-kjv.db").asFile.path,
        repoRoot.file("shared/feed-engine/src/jvmTest/resources/kjv-units.tsv").asFile.path,
    )
}

tasks.test { workingDir = repoRoot.asFile }
