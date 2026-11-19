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
    implementation(project(":shared:stories"))
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
        repoRoot.dir("content/editorial").asFile.path,
    )
}

// ./gradlew :pipeline:extractParagraphs -Pusfx=/path/to/eng-web.usfx.xml
// Regenerates content/editorial/paragraphs.txt from a public-domain USFX edition (positions only, no text).
tasks.register<JavaExec>("extractParagraphs") {
    group = "content"
    description = "Extracts paragraph starts from a USFX Bible into content/editorial/paragraphs.txt"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bible.pipeline.MainKt")
    args(
        "extract-paragraphs",
        (project.findProperty("usfx") as String?) ?: "missing -Pusfx=<file>",
        repoRoot.file("content/editorial/paragraphs.txt").asFile.path,
    )
}

// ./gradlew :pipeline:buildStories  →  builds the bundled story catalogue from the CSV, curation and editorial files.
tasks.register<JavaExec>("buildStories") {
    group = "content"
    description = "Builds composeResources/files/stories.json from content/catalogue and content/stories"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bible.pipeline.MainKt")
    args(
        "build-stories",
        repoRoot.file("content/catalogue/bible_story_catalogue.csv").asFile.path,
        repoRoot.file("content/stories/curation.tsv").asFile.path,
        repoRoot.file("content/stories/editorial.tsv").asFile.path,
        repoRoot.file("composeApp/src/commonMain/composeResources/files/stories.json").asFile.path,
    )
}

tasks.test { workingDir = repoRoot.asFile }
