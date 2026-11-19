plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
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
    implementation(libs.kotlinx.serialization.json)
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

// ---- Story art (docs/story-art.md) -------------------------------------------------------------------------
// Briefs, overrides, the review log and the manifest are in content/art (git). Candidates are in art-work/ (not in
// git; override with -PartWork=<dir>). Provider keys and R2 credentials come from environment variables only.

val artFiles = listOf(
    repoRoot.file("content/art/briefs.jsonl").asFile.path,
    repoRoot.file("content/art/review.tsv").asFile.path,
    repoRoot.file("content/art/manifest.json").asFile.path,
    ((project.findProperty("artWork") as String?) ?: repoRoot.dir("art-work").asFile.path),
)

fun artTask(name: String, desc: String, vararg args: () -> String) = tasks.register<JavaExec>(name) {
    group = "art"
    description = desc
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bible.pipeline.MainKt")
    argumentProviders.add(CommandLineArgumentProvider { args.map { it() } })
}

// ./gradlew :pipeline:artPlan  →  content/art/briefs.jsonl from stories.json and content/art/overrides.tsv
tasks.register<JavaExec>("artPlan") {
    group = "art"
    description = "Plans one cover brief per story into content/art/briefs.jsonl"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bible.pipeline.MainKt")
    args(
        "art-plan",
        repoRoot.file("composeApp/src/commonMain/composeResources/files/stories.json").asFile.path,
        repoRoot.file("content/art/overrides.tsv").asFile.path,
        artFiles[0],
    )
}

// ./gradlew :pipeline:artGenerate -Pprovider=fal -Plimit=10 [-Pmodel=fal-ai/flux/dev] [-Ponly=slug1,slug2]
// Costs money with a real provider. -Pprovider=dry-run makes free placeholders to try the workflow.
artTask(
    "artGenerate", "Generates 4 candidates for stories that need art (once per brief)",
    { "art-generate" }, { artFiles[0] }, { artFiles[1] }, { artFiles[2] }, { artFiles[3] },
    { (project.findProperty("provider") as String?) ?: error("Pass -Pprovider=fal, openai or dry-run") },
    { (project.findProperty("model") as String?) ?: "-" },
    { (project.findProperty("limit") as String?) ?: "10" },
    { (project.findProperty("only") as String?) ?: "-" },
)

// ./gradlew :pipeline:artReview  →  art-work/review.html
artTask("artReview", "Writes the review page for candidates awaiting a decision",
    { "art-review" }, { artFiles[0] }, { artFiles[1] }, { artFiles[2] }, { artFiles[3] })

// ./gradlew :pipeline:artApplyReview -Pdecisions=~/Downloads/decisions.tsv -Previewer=Henry
artTask(
    "artApplyReview", "Adds downloaded review decisions to content/art/review.tsv",
    { "art-apply-review" }, { artFiles[0] }, { artFiles[1] }, { artFiles[2] }, { artFiles[3] },
    { (project.findProperty("decisions") as String?) ?: error("Pass -Pdecisions=<decisions.tsv>") },
    { (project.findProperty("reviewer") as String?) ?: error("Pass -Previewer=<your name>") },
)

// ./gradlew :pipeline:artPublish -Pbase=https://cdn.example.com      (R2 credentials from the environment)
// ./gradlew :pipeline:artPublish -Plocal=/tmp/art-out                 (writes files to a folder instead)
artTask(
    "artPublish", "Publishes approved images and writes the manifest the app reads",
    { "art-publish" }, { artFiles[0] }, { artFiles[1] }, { artFiles[2] }, { artFiles[3] },
    { repoRoot.file("composeApp/src/commonMain/composeResources/files/art.json").asFile.path },
    { (project.findProperty("base") as String?) ?: System.getenv("ART_CDN_BASE") ?: "-" },
    { (project.findProperty("local") as String?) ?: "r2" },
    { if (project.hasProperty("allowDryRun")) "true" else "false" },
)

tasks.test { workingDir = repoRoot.asFile }
