# VerseUp

A free, offline-first Bible app with an immersive vertical Scripture feed, Story Mode and Regular Mode.
Kotlin Multiplatform + Compose Multiplatform for Android and iOS. Logos are in `branding/`. No ads, no subscriptions, ever.

The product and technical plan is in [docs/immersive-scripture-proposal.md](docs/immersive-scripture-proposal.md).
Image rules are in [docs/imagery-policy.md](docs/imagery-policy.md).

## Repository layout

| Path | What it is |
|---|---|
| `shared/core-model` | Canonical verse IDs (BBCCCVVV, KJV versification), books, passages, reference parser |
| `shared/feed-engine` | The verse grouper (1/3/5/10/Auto verses per card) shared by every reading mode |
| `shared/bible-data` | SQLDelight schema for the read-only `bible-<translation>.db` |
| `shared/stories` | Story Mode model (stories, steps, parallel accounts), discovery and story pages |
| `composeApp` | Shared Compose UI (Android, iOS, desktop preview) |
| `androidApp` | Android application shell |
| `iosApp` | iOS application shell (XcodeGen project) |
| `pipeline` | Content tools: KJV import, later pack building and image jobs |
| `backend` | Ktor API (accounts, sync, admin) with PostgreSQL |
| `deploy` | Docker Compose, Caddy and backup scripts for the VPS |
| `content` | Source texts (KJV OSIS from eBible.org, public domain), the story catalogue, and story curation (`content/stories`) |

## Build

Requirements: JDK 17+ (21 recommended). Android Studio for Android, Xcode 16+ and XcodeGen for iOS.

```bash
./gradlew :androidApp:installDebug                 # Android device or emulator
./gradlew :composeApp:run                          # desktop preview of the shared UI
cd iosApp && xcodegen && open iosApp.xcodeproj     # iOS (run from Xcode)

./gradlew -Pbible.android=false :shared:core-model:jvmTest :shared:feed-engine:jvmTest :backend:test
./gradlew :pipeline:importKjv                      # rebuild the bundled KJV database
./gradlew :pipeline:buildStories                   # rebuild stories.json after editing content/stories or the catalogue
```

`-Pbible.android=false` builds everything except Android, for machines without the Android SDK.

## Principles

- Scripture text is never altered. Stories, images, questions and plans reference canonical verse ranges.
- Everything core works offline. Registration is optional.
- Images are generated once, reviewed by people, and shared by every user.
