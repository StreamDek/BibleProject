# Story art

How story cover images are planned, generated, reviewed and published. This follows proposal §6 and the
[imagery policy](imagery-policy.md). Every image is generated once, approved by a person, and shared by every user.
Phones only download published files from the CDN. Nothing is generated on a device or per user.

## What lives where

| What | Where | In git |
|---|---|---|
| Scene briefs, one per story | `content/art/briefs.jsonl` (made by `artPlan`) | yes |
| Editor corrections to briefs | `content/art/overrides.tsv` | yes |
| Review log: who approved or rejected what, and when | `content/art/review.tsv` (append only) | yes |
| Published images: story → image hash, sizes, blurhash | `content/art/manifest.json`, and the copy the app bundles in `composeResources/files/art.json` | yes |
| Generated candidates waiting for review | `art-work/candidates/<story>/<brief hash>-r<round>/` | no |
| Published files | R2: `img/<sha256>.<360,720,1080>.jpg`, `manifest/art.json` | no |

Provider keys and R2 credentials are read from environment variables only. They are never written to the repository.

## Steps

All steps are Gradle tasks in the `art` group. Run them from the repository root.

1. **Plan.** `./gradlew :pipeline:artPlan` writes one brief per story from its period, kind and introduction. Each
   brief records the depiction type (direct, historical context, symbolic, atmospheric), whether people appear, and
   whether God or Jesus may appear, which is only ever as a light silhouette with no face. The planner's scene text
   is a first draft. Correct any brief in `overrides.tsv` and run `artPlan` again. CI fails if `briefs.jsonl` is
   out of date.

2. **Generate.** This costs money with a real provider.
   ```
   FAL_KEY=...         ./gradlew :pipeline:artGenerate -Pprovider=fal -Plimit=10
   OPENAI_API_KEY=...  ./gradlew :pipeline:artGenerate -Pprovider=openai -Plimit=10
                       ./gradlew :pipeline:artGenerate -Pprovider=dry-run -Plimit=3   # free placeholders, for trying the workflow
   ```
   - It makes 4 candidates per story. `-Ponly=slug1,slug2` limits it to the named stories, and `-Pmodel=` picks a
     model (defaults: `fal-ai/flux/dev`, `gpt-image-1`).
   - It never generates the same thing twice. Stories are skipped when they already have a published image, are
     approved, are waiting for review, or were rejected and the brief has not changed since.
   - Each candidate set keeps the model, prompt and seeds beside the images.

3. **Review.** `./gradlew :pipeline:artReview` writes `art-work/review.html`.
   - Open it in a browser. Click the best candidate to approve it, or reject the set with a reason.
   - "Reject" means the brief needs changes in `overrides.tsv`. "Regenerate" means the same brief with new seeds.
   - Download `decisions.tsv`, then run
     `./gradlew :pipeline:artApplyReview -Pdecisions=<path to decisions.tsv> -Previewer=<name>`.
     This checks each decision and appends it to `review.tsv`.

4. **Publish.**
   ```
   R2_ACCOUNT_ID=... R2_BUCKET=... R2_ACCESS_KEY_ID=... R2_SECRET_ACCESS_KEY=... \
     ./gradlew :pipeline:artPublish -Pbase=https://<cdn domain>
   ```
   - It crops each approved image to 9:16 and makes 360, 720 and 1080 px wide JPEGs plus a blurhash.
   - It uploads them under content-addressed names with `Cache-Control: immutable`, skipping files R2 already has.
   - It updates `manifest.json` and the app's `art.json`. Commit both, and the next app build shows the art.
   - Use `-Plocal=<folder>` instead of R2 to try it out. Dry-run placeholders are refused unless you pass `-PallowDryRun`.

## In the app

`StoryArt` shows the published image from the CDN over its blurhash preview. Coil keeps it in the device's memory
and disk caches. Stories without a published image keep the placeholder art. When a story has a published image,
its introduction card says "Image: AI-generated illustration · <depiction> · Reviewed by our editors".

## Not done yet

- **Automatic vision check** (proposal §6.2 step 4). It needs a vision-model key. Human review is mandatory either way.
- **AVIF and WebP variants.** Images are JPEG for now because every platform decodes JPEG. AVIF would save about
  40% of the bytes and can be added in the publisher without changing the app.
- **Scene images inside stories, and image packs for offline use** (Tier B and §6.5).
- **The "Landscapes and settings only" setting.** The manifest already records which images show people.
- **Review in the admin platform** instead of the local review page, once the admin platform exists.
