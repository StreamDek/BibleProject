# Immersive Scripture Experience: Product and Technical Proposal

**Status:** Draft for approval (revision 2). Nothing here has been implemented.
**Date:** 19 November 2026
**Revision 2:** This is a new project built from scratch (there is no existing code to reuse). It launches with the KJV only, and the backend is self-hosted on your VPS.
**Scope:** Section 24 of the brief "Bible App: Immersive Scripture Experience, Story Mode & Interactive Learning"

---

## 0. Summary

This proposal turns the app into a free, offline-first, vertically paged Scripture reader. It has two modes, Story Mode and Regular Mode, and both run on **one shared Scripture Feed Engine**.

I agree with your single-engine recommendation and have made it the base of the architecture. The engine renders a sequence of Scripture cards. Each mode is only a **Feed Source**, meaning the code that decides which verses come next and in what order. Grouping, paging, image loading, gestures, preloading, accessibility and reading position are written once.

### Key decisions

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | Feed architecture | One `FeedEngine` with pluggable `FeedSource`s (Canonical, Plan, Story) | One scroll implementation to tune and test on both platforms |
| D2 | Position model | Reading position is a **canonical verse ID** (e.g. `40002008` = Matthew 2:8), not a card index | Survives changes of translation, verse density and mode |
| D3 | Content identity | Stories, images, questions and plans reference **canonical verse ranges**, never translation text | Nothing is tied to one translation (§19 of the brief) |
| D4 | Verse grouping | Grouping is computed on the device from boundary weights (pericope, paragraph, sentence) plus a character budget | Never splits a sentence just to hit 5 verses, and adapts to font size |
| D5 | Images | Generated ahead of time in batches, linked to **scenes** (canonical ranges), shared by consecutive cards, and human-approved before publishing | Keeps cost bounded (about 3,000 images, not one per card), images are reused, and quality is controlled |
| D6 | Image delivery | Images and content packs behind the Cloudflare CDN (free plan). The files are stored in Cloudflare R2 by default, or on your VPS if you want everything on your server. AVIF/WebP in three sizes, inline blurhash placeholder | The CDN serves almost all traffic, so the VPS's bandwidth and disk are not used up by images |
| D7 | Local storage | SQLDelight with three separate SQLite files: Bible text (read-only, per translation), curated content (read-only, versioned), and user data (read/write, synced) | Content updates never touch user data, and sync is limited to one small database |
| D8 | Backend | **Self-hosted on your VPS:** a Kotlin/Ktor API with PostgreSQL for accounts, sync and admin, a pipeline worker, and the admin web app, all in Docker Compose behind Caddy (HTTPS). Content packs are static files on the CDN | Kotlin end to end and light on resources. Reading never depends on the API, so a VPS outage only pauses sync and admin |
| D9 | Learning | Pre-generated question banks with Scripture-linked answers that an editor validates. FSRS spaced repetition runs locally | Fits the brief's guardrails; works fully offline |
| D10 | Translations | **Launch with KJV only.** Build the translation layer for several translations from day one, so ERV, NCV or others can be added later as content packs without an app release | KJV is free to use. ERV and NCV need permission or a licence (see §2) |
| D11 | Codebase | **Greenfield monorepo**: KMP/CMP app, a web admin, the Ktor API and a pipeline worker in one repository | No existing code. One repository keeps shared canonical IDs and content schemas in sync |

### Starting point

This is a **new project built from scratch.** The only existing asset is the story catalogue (`bible_story_catalogue.csv`, reviewed in §1). Features from your earlier requirements, such as devotionals, Verse of the Day, teachings, push notifications, scheduling and audit logs, are **built new** as part of this plan rather than carried over.

---

## 1. Starting point and existing assets

### 1.1 What exists

| Asset | Location | State |
|---|---|---|
| Story catalogue | `C:\Dev\BibleProject\bible_story_catalogue.csv` | 652 rows: `id, testament, book, title, scripture_reference, content_type, review_status` |
| Mobile app, admin platform, backend | None | **Built from scratch** in this plan (§3.6 sets up the repository) |

### 1.2 Story catalogue analysis

This is a strong starting point. It is reusable as-is as the seed for the story content model (§5), with some restructuring.

- **Coverage:** 652 entries across all 66 books (378 Old Testament, 274 New Testament). 573 are tagged `Narrative` and 79 are `Teaching/Poetry`. All 652 are marked `Needs editorial review`.
- **Shape:** Each row is **one passage from one book**. The brief's story model needs several passages per story (primary, related and parallel), so rows become *passage segments* that are grouped into stories.
- **Parallel accounts are split into separate rows.** Twenty titles appear more than once. Most of them are parallel accounts that should become one story with several accounts:
  - Feeding the five thousand: Matthew 14:13-21, Mark 6:30-44, John 6:1-15
  - Triumphal entry: Matthew 21:1-11, Mark 11:1-11, John 12:12-19
  - Last Supper: Matthew 26:17-30, Mark 14:12-26, Luke 22:7-38
  - Also: storm calmed, walking on water, transfiguration, anointing at Bethany, before Pilate, crucifixion, burial, resurrection, appearances, temple dedication, Queen of Sheba, ark brought to Jerusalem, Hezekiah's illness, fall of Jerusalem
  - Some of the shared titles are **not** parallels and must stay separate: "Joshua commissioned" (Numbers 27 and Joshua 1 are different events), "Day of the Lord" (Joel 3 and Malachi 4), and "New life in Christ" (Romans 6-8 and Colossians 3, which are thematic).
- **Incomplete parallels.** The Birth of Jesus is split between STORY-0379 (Matthew 1) and STORY-0468 (Luke 2:1-20). Matthew 2:1-12 and Luke 1:26-38, both named in your brief, are not linked. Several Gospel parallels are missing an account, for example Mark's crucifixion under that title.
- **Reference formats vary.** There are 261 whole-chapter references (e.g. "Genesis 3"), 19 ranges that cross chapters (e.g. "Genesis 2:4-25"), and one range with a gap ("Matthew 13:24-30,36-43"). The data model must support **ranges with gaps**.
- **Missing fields** that the brief requires: categories (characters, periods, miracles, parables, themes), related passages, historical context, summaries, questions, cover image and key characters.

**Recommendation:** Import the CSV into the admin platform as `PassageSegment` drafts. Run a one-off AI-assisted clustering pass that proposes story groupings, parallel accounts and categories, and have an editor confirm it. I expect roughly **420 to 480 stories** from the 652 rows.

### 1.3 Building from scratch: what it means for the plan

- **No migration work.** Bookmarks, notes and plans are designed around canonical verse IDs from the start, so there is no legacy schema to convert.
- **Features from your earlier requirements are in scope as new work:** devotionals, Verse of the Day, teachings, push notifications, content scheduling, publication history, admin security and audit logs. They appear in the admin plan (§10) and the roadmap (§13).
- **Phase 0 now includes project setup:** repository, build, CI, design system and environments (§3.6, §13).
- **The catalogue is the only content to import.** It becomes the seed for Story Studio (§10).

---

## 2. Translation licensing (verified 19 Nov 2026)

**Decision: launch with KJV only.** The findings below for ERV and NCV are kept for when you decide to add them.

| Translation | Holder | Status | Can it ship offline in a free app? |
|---|---|---|---|
| **KJV** | Public domain outside the UK. In the UK, Crown rights under letters patent cover *printing*, administered by Cambridge University Press | Free to use. Use a clean source such as the eBible.org/CrossWire KJV | **Yes.** UK Crown rights apply to printed editions; digital apps commonly ship KJV. Note this in your legal review |
| **ERV** (Easy-to-Read Version) | Bible League International | Free for non-commercial electronic distribution **without modification**, but it **may not be hosted on any server without written permission**. Quotations up to 1,000 verses are allowed without permission | **Not without written permission.** Our downloadable packs on a CDN count as server hosting. Permission seems likely because the app is free and non-commercial: request it through bibleleague.org |
| **NCV** (New Century Version) | HarperCollins Christian Publishing (Thomas Nelson) | Up to 500 verses without permission. Use in a smartphone app requires a separate request. **Using the whole translation requires a licence and is subject to a fee.** Turnaround is 6 to 8 weeks | **Not without a paid licence.** The fee is unknown and must be requested. Expect them to ask about distribution, price (free) and business model |

**Implications for the design**

- The licence terms forbid altering the text. Our "canonical layer is never modified" guardrail already meets this. Card overlays and grouping only present the text and never change it.
- Section headings are part of a translation's copyrighted material. Use **our own editorial headings** (from the story and pericope model) rather than the publisher's.
- Shareable verse cards are *quotations*. Each one must include the translation attribution (e.g. "(ERV)") and stay within quotation limits.
- **Text-to-speech and audio** of NCV or ERV may need separate permission. KJV is unrestricted.
- **Launching with KJV only** means the app ships without waiting on any third party, and the whole feature set (offline packs, audio, shareable cards) is open to us. KJV's archaic language matters most for the readers the brief targets, people who struggle with traditional reading. Story intros, summaries and the "Explore the Context" sheet help with that, and a small glossary of archaic words (thee, thou, -eth, "suffer" meaning "allow") is a cheap addition (§14).
- **Adding a translation later** is a content release, not an app release. The translation layer, the versification map and the licence metadata (§10) are built from the start. Openly licensed modern options that need no permission are the **Berean Standard Bible** (public domain since 2023), the **World English Bible** (public domain) and the **unfoldingWord Simplified Text** (CC BY-SA 4.0). For ERV or NCV, start the requests about three months before you want them in the app.

Sources: [Bible League licence agreement](https://www.bibleleague.org/license-agreement), [ERV copyright notice (eBible)](https://ebible.org/engerv/copyright.htm), [HarperCollins Christian permissions](https://www.harpercollinschristian.com/permissions/), [KJV copyright notes (eBible)](https://ebible.org/kjv/copr.htm).

---

## 3. Updated product architecture (brief item 1)

### 3.1 How the pieces fit

```
                    ┌──────────────────────── App shell ────────────────────────┐
                    │  Home   ·   Stories   ·   Read   ·   Learn   ·   Me       │
                    └──────┬─────────────┬───────────┬───────────┬──────────────┘
                           │             │           │           │
                ┌──────────▼───┐  ┌──────▼─────┐ ┌───▼────────┐  │
                │ StorySource  │  │ Canonical  │ │ PlanSource │  │  (Feed Sources:
                │ (story steps)│  │ Source     │ │ (day's     │  │   what comes next)
                │              │  │ (book/ch)  │ │  readings) │  │
                └──────┬───────┘  └──────┬─────┘ └────┬───────┘  │
                       └─────────────────┼────────────┘          │
                                ┌────────▼─────────┐             │
                                │  FeedEngine      │◄── ReadingPrefs (density, font,
                                │  · Grouper       │    images on/off, motion, theme)
                                │  · CardBuilder   │
                                │  · Window loader │◄── ImageResolver (scene → image)
                                │  · Position mgr  │
                                └────────┬─────────┘
                                ┌────────▼─────────┐        ┌─────────────────────┐
                                │ ScriptureFeed UI │───────►│ Learning (quizzes,  │
                                │ (VerticalPager)  │ hooks  │ memorize, review)   │
                                └──────────────────┘        └─────────────────────┘
```

### 3.2 The Feed Engine contract

```kotlin
// A FeedSource only decides WHICH canonical verses, in WHAT order, with WHAT interludes.
interface FeedSource {
    val id: FeedSourceId                       // e.g. Canonical(book=42), Story("birth-of-jesus"), Plan("nt-90", day=12)
    suspend fun segments(): List<FeedSegment>  // ordered
}

sealed interface FeedSegment {
    data class Scripture(val range: VerseRange, val label: String?) : FeedSegment   // canonical verses
    data class Interlude(val kind: InterludeKind, val contentId: String) : FeedSegment
    // InterludeKind: StoryIntro, Summary, Context, Quiz, AccountSwitcher, PlanDayComplete
}

class FeedEngine(
    private val bible: BibleTextRepository,     // per-translation text
    private val grouper: VerseGrouper,          // §3.4
    private val images: ImageResolver,          // §6
    private val prefs: StateFlow<ReadingPrefs>,
) {
    fun open(source: FeedSource, anchor: VerseId?): Flow<FeedState>
}
```

- **Regular Mode, free reading:** `CanonicalSource(book, chapter)`. The feed spans a whole book so that a swipe continues into the next chapter, with a slim chapter divider card between chapters.
- **Regular Mode, reading plan:** `PlanSource(planId, day)` gives the day's ranges followed by a gentle "Day complete" interlude.
- **Story Mode:** `StorySource(storyId, accountId?)` gives an intro interlude, the scripture segments in narrative order, summary interludes at milestones, and an optional quiz interlude at the end.
- Interludes are **not Scripture**. They have a distinct visual style and an "Explanation" label (§4.4), and every interlude can be skipped with one swipe.

### 3.3 Reading position and switching modes

- The global position is `ReadingAnchor(verseId, sourceId)`, saved to the user database (debounced every card change, so restoring after a restart is immediate).
- **Story → Regular:** "Open in Bible" on any card opens `CanonicalSource` at that card's first verse. Back returns to the story at the same card.
- **Regular → Story:** if the current verse belongs to a published story, the action rail shows a "Part of: The Birth of Jesus" chip that opens the story at that verse.
- **Changing translation or density** re-groups the feed and scrolls to the card that contains the anchor verse. The reader never loses their place.

### 3.4 Verse grouping (density) algorithm

The input is the source's verse range plus a **boundary weight** between every pair of adjacent verses:

| Weight | Boundary | Origin |
|---|---|---|
| 4 | Chapter break or story-segment break | Canonical |
| 3 | Pericope / section break (our editorial section list, about 2,500 sections across the Bible) | Canonical, curated once |
| 2 | Paragraph or poetry stanza break | Per translation, from source markup |
| 1 | Sentence end (`.` `?` `!` closing quote) | Per translation, computed when imported |
| 0 | Mid-sentence or mid-quotation | Per translation (**never break here**) |

The **algorithm** is a greedy search with a short look-ahead. It runs in a few milliseconds per chapter and results are cached per `(translation, density, range)`:

1. Target = N verses (1, 3, 5 or 10). The allowed window is roughly N±40%, and a card also has a **character budget** that depends on font size and screen height, so ten verses of Esther 8 do not overflow.
2. From the card's start, consider break points inside the window. Choose the one with the **highest weight**, breaking ties by closeness to N.
3. Never break at weight 0 unless a single sentence exceeds the hard budget (e.g. Ephesians 1:3-14 in some translations). In that case, break at the verse edge and show a small "continues" mark.
4. **Automatic (narrative) mode** ignores N and groups by pericope, splitting only if the character budget is exceeded. Six verses that end a story stay together.
5. Verse numbers are rendered from source data. **Text is never re-flowed across verse boundaries** or changed.

Grouping is **deterministic**, so the same inputs always give the same cards. It is also unit-tested with golden files for tricky passages (Psalm 119, genealogies, Esther 8:9, Ephesians 1, Job's speeches).

### 3.5 Module layout (Kotlin Multiplatform)

```
:core:model          canonical IDs, VerseRange, versification maps
:core:db             SQLDelight: bible-<tr>.db, content.db, user.db
:core:sync           change log, push/pull, merge
:core:packs          manifest, download, verify, install
:feature:feed        FeedEngine, Grouper, ScriptureFeed composables   ← shared by all modes
:feature:stories     catalogue, discovery, StorySource
:feature:read        CanonicalSource, PlanSource, Classic reader, Jump-to
:feature:learn       quizzes, memorize, FSRS scheduler
:feature:study       Explore the Context sheet, cross-refs, maps, timeline
:feature:account     auth, guest merge
:app:android / :app:ios   platform shells, notifications, share sheet, TTS
```

### 3.6 Repository and tech stack (greenfield)

One monorepo, so the canonical ID scheme and content schemas are defined once and shared:

```
bible-app/
├── app/            KMP + Compose Multiplatform (modules from §3.5), Android + iOS
├── shared-schema/  canonical IDs, content-pack schema (JSON Schema + Kotlin models)
├── admin/          web admin platform (§10)
├── backend/        Ktor API (auth, sync, admin API), Flyway migrations
├── deploy/         Docker Compose, Caddyfile, backup scripts for the VPS
├── pipeline/       worker: Bible import, pack builder, image + LLM batch jobs
├── content/        source data: KJV (USFM/OSIS), story catalogue CSV, pericope list
└── docs/           this proposal, ADRs, imagery policy
```

| Layer | Choice | Alternatives considered |
|---|---|---|
| Mobile | Kotlin 2.x, Compose Multiplatform, Koin, SQLDelight, Ktor client, Coil 3, kotlinx.serialization, kotlinx.datetime | Room KMP (fine, but SQLDelight gives SQL-first control over FTS5 and attached databases) |
| Admin web | **Next.js (TypeScript)** calling the Ktor admin API, with the card preview rendered by a **Compose Multiplatform Web (Wasm)** build of `:feature:feed` so editors see exactly what users see | Compose for Web for the whole admin (forms and tables are still immature); a Kotlin/Ktor server-rendered admin (less tooling for complex editors) |
| Backend | **Ktor + PostgreSQL 16 on your VPS** (Docker Compose, Caddy for HTTPS) | Self-hosted Supabase on the VPS, or Firebase (§11) |
| Pipeline worker | Kotlin (shares `shared-schema` and the grouper), run as a container on a schedule or queue | Python (larger image and ML tooling ecosystem, but duplicates the schema code) |
| Content delivery | Cloudflare CDN in front of R2 (recommended) or in front of the VPS | S3 + CloudFront (much higher egress cost, §12) |
| CI/CD | GitHub Actions: build and test on every PR, iOS build on a macOS runner, Fastlane for store uploads | Bitrise or Codemagic for faster iOS builds if Actions minutes get expensive |
| Quality | Unit tests (grouper golden files, sync merge), screenshot tests for cards, Maestro end-to-end flows on both platforms | — |

---

## 4. UI/UX design (brief item 2)

The visual direction follows your reference: warm and cinematic for Story Mode, quiet atmospheric landscapes for Regular Mode. Scripture always has the highest contrast on screen.

### 4.1 Home

```
┌────────────────────────────┐
│  Good morning              │
│ ┌────────────────────────┐ │
│ │ CONTINUE               │ │  ← resumes the last anchor (story or book)
│ │ The Birth of Jesus     │ │
│ │ Luke 2:8 · 4 of 9      │ │
│ └────────────────────────┘ │
│  Today's reading           │  ← each active plan, one line each
│  NT in 90 days · Day 12 ✓  │
│  Verse of the Day ▸        │  ← from earlier requirements
│  Review 3 verses ▸         │  ← memorization due, only if any are due
│  Stories for you  ▸ ▸ ▸    │  ← horizontal covers
│  Devotional ▸              │  ← from earlier requirements
├────────────────────────────┤
│ Home Stories Read Learn Me │
└────────────────────────────┘
```

There are no badges, counters or red dots. Home is calm and leads with **Continue**.

### 4.2 Story discovery

- A **search** bar at the top. It searches story titles, characters, places and references entirely offline using SQLite FTS5.
- **Continue Story** row.
- Shelves: **Life of Jesus**, **Old Testament**, **New Testament**, **Miracles**, **Parables**, **People** (character avatars), **Periods** (Patriarchs → Exodus → Judges → Kingdom → Exile → Return → Jesus → Early Church), **Themes**.
- A **"Journey through the Bible"** path: stories in chronological order, which can be followed in sequence or entered at any point.
- A story detail page shows the cover, introduction, sources (e.g. "Matthew 1:18-25 · Luke 2:1-20 · +2"), card count, about how many minutes of reading, a download indicator, and **Begin**.

### 4.3 The full-screen Scripture card

```
┌────────────────────────────┐
│ [image, full bleed]        │
│                            │
│                       ♡ ◷  │  ← action rail (right edge, 40% opacity at rest)
│                       ✎ ⇪  │     Bookmark · Note · Share · Study · Memorize · Aa
│ ▓▓▓▓ adaptive gradient ▓▓▓ │
│  LUKE 2:8-14 · KJV         │  ← reference (tap → full chapter in context)
│                            │
│  ⁸ And there were in the   │  ← Scripture: serif, 20-22sp default,
│  same country shepherds    │     line height 1.5, max ~34 characters per line
│  abiding in the field…     │
│  ⁹ And, lo, the angel of   │
│  the Lord came upon them…  │
│                            │
│  ━━━━━━━━━━──────── 4/9    │  ← quiet progress (story / chapter)
└────────────────────────────┘
```

- **Legibility engine.** When an image is processed, the pipeline stores three measurements: the luminance of the image's lower and upper thirds, a **safe text region**, and a dominant colour. At runtime the card places text in the safe region, picks gradient strength from the luminance (stronger gradient over bright images), and checks that the text reaches **WCAG AA contrast (≥ 4.5:1)** against the rendered gradient. If it does not, the card dims the image further. A user dimming slider overrides this.
- **Typography.** Default to a high-quality open-source serif for Scripture (e.g. Literata or Source Serif 4) and a sans for the interface. Use small-caps verse numbers and real typographic quotes. Respect dynamic type and system font scaling.
- **Light, dark and text-only themes.** In the text-only theme the image is replaced by a paper or ink colour field. It is a fully supported mode, not a degraded one.
- **Distraction-free.** Tapping the image hides the rail, reference and progress. Tapping again shows them. The state persists.

### 4.4 Interludes (explanatory content)

Interludes use a solid card style with no Scripture typography. They carry a visible label: "**Explanation**", "**Context**" or "**Summary**". They are written by our editors and are not part of the Bible text. Each one can be expanded, collapsed or swiped past. Summaries appear only at story milestones and at the end, never after every card.

**Gospel account switcher.** In multi-account stories, a segmented control shows "Matthew · Mark · Luke · John". Switching keeps the narrative step and swaps the source passage. Each account's wording stays separate. **Nothing is ever merged into a combined text.**

### 4.5 Gestures (no conflicts)

| Gesture | Action |
|---|---|
| Swipe up / down | Next / previous card (pager) |
| Tap the image area | Toggle distraction-free mode |
| Tap the reference | Open the chapter in context (Classic reader sheet, scrolled to the verse) |
| Long-press a verse | Verse selection mode: copy, highlight, bookmark, note, memorize, share |
| Tap Study | "Explore the Context" bottom sheet |
| Two-finger tap, or the Aa button | Reading settings |

Horizontal swipes are deliberately **left unassigned** in the feed, so they never clash with the iOS back gesture or Android predictive back. The account switcher uses taps.

### 4.6 Reading controls (Aa sheet)

Verses per card (1 / 3 / 5 / 10 / Auto) · Font size · Font (Serif / Sans / Dyslexia-friendly) · Theme (Light / Dark / Sepia / System) · Images (On / Dimmed / Off) · Image content preference (People and scenes / Landscapes only; see §13) · Motion (Full / Reduced) · Feed or Classic reader · Translation, with a compare toggle.

### 4.7 Question interfaces

The quiz is a sheet that slides up over the feed, so it never feels like a gate.

- **Multiple choice:** four large targets. The answer shows a soft green or neutral colour, then the explanation and a tappable reference ("Luke 2:4-7").
- **True / false:** two buttons, with the same feedback.
- **Fill in the missing word:** the verse is shown with chips for the hidden words and a word bank.
- **Verse ordering:** drag handles, with a "Show me" helper after two attempts.
- **Who said this?:** a quotation card plus avatars of the candidate speakers.
- **Reference matching:** two columns, tap to pair.
- **Reflection:** an open prompt with an optional private note saved to Notes. It is never graded.

Wrong answers produce text like "Not quite. Here's where it's found." with the passage. The user can retry, skip or "Read the passage again". There are no scores, timers or streak penalties.

---

## 5. Story content model (brief item 3)

### 5.1 Canonical identifiers

- `VerseId = BBCCCVVV` (book 01-66, chapter, verse) in the **KJV/English Protestant versification**, which serves as the canonical scheme.
- `VerseRange(start: VerseId, end: VerseId)`. A `Passage` is an ordered list of ranges, so it can represent gaps such as Matthew 13:24-30,36-43.
- `VersificationMap(translationId)` handles the rare cases where a translation numbers verses differently (e.g. 3 John 1:14-15, Revelation 12:18 vs 13:1, Malachi 4) or omits a verse that is text-critically disputed (e.g. Matthew 17:21 in some modern translations). Missing verses render as a small note ("Some manuscripts add verse 21") using that translation's own footnote, never generated text.

### 5.2 Entities

```
Story ─┬─ id, slug, title, subtitle, coverImageId, testament, period, status, version
       ├─ categories[]  (LifeOfJesus, Miracle, Parable, Character:david, Theme:faith…)
       ├─ characters[]  → Character(id, name, aliases, bio[editorial], firstAppearance)
       ├─ places[]      → Place(id, name, lat/lon, certainty: known|likely|disputed)
       ├─ intro         → EditorialBlock
       ├─ context       → EditorialBlock (historical / geographical)
       ├─ steps[] (ordered narrative)
       │    StoryStep ─┬─ id, title, order
       │               ├─ accounts[]  → StoryAccount(book, passage: Passage, isPrimary)
       │               ├─ sceneIds[]  → Scene (image association, §6)
       │               └─ milestone?  → EditorialBlock(Summary)
       ├─ relatedPassages[] → RelatedPassage(passage, relation: prophecy|parallel|echo|background, note)
       ├─ summary       → EditorialBlock
       ├─ lessons[]     → EditorialBlock
       ├─ questionBankId
       └─ memoryVerses[] → Passage

EditorialBlock: id, kind, body (markdown), citations[] (Passage), authorId, reviewerId,
                status (draft|in_review|approved|published|archived), version, aiAssisted: bool
```

### 5.3 Rules that protect Scripture

1. **A story never stores Bible text.** It stores references only. Text always comes from the translation database at render time, so it is impossible for a story to contain altered Scripture.
2. **Editorial text is a different type.** `EditorialBlock` and Scripture are rendered by different components with different styles and labels, and the type system prevents mixing them.
3. **Narrative order is editorial and is labelled as such.** If steps are arranged chronologically across books, the story header says "Arranged in chronological order by our editors · Sources: …".
4. **Parallels stay separate.** The Birth of Jesus becomes, for example:

| Step | Accounts |
|---|---|
| 1. The angel visits Mary | Luke 1:26-38 |
| 2. Joseph's dream | Matthew 1:18-25 |
| 3. Birth in Bethlehem | Luke 2:1-7 |
| 4. Shepherds and angels | Luke 2:8-20 |
| 5. The Magi | Matthew 2:1-12 |

Related passages: Isaiah 7:14 (prophecy), Micah 5:2 (prophecy). Where a step has more than one account (e.g. the feeding of the five thousand), the account switcher appears.

5. **Every published object is versioned.** A user's progress refers to `(storyId, stepId)`. Published versions are immutable, and edits create a new version, so progress and quiz history stay valid.

### 5.4 Taxonomy

Categories are a controlled vocabulary managed in the admin platform: Testament, Period (12 eras), Event type (Creation, Covenant, Exodus, Conquest, Kingdom, Prophecy, Exile, Incarnation, Ministry, Miracle, Parable, Passion, Resurrection, Church, Revelation), Characters (about 150 to start), Places (about 200 with coordinates and certainty), Themes (about 40).

---

## 6. AI image strategy (brief item 4)

### 6.1 Unit of illustration: the Scene

One image per card would mean about 6,200 images at 5 verses per card, and the number would change with every density setting. Instead, an image is linked to a **Scene**, which is a canonical range with one visual subject (for example, Luke 2:8-14, "shepherds in the field at night, angel appears"). A card shows the scene that overlaps its **first verse** the most. Consecutive cards in the same scene use **2 or 3 variants** (different crops or compositions of the same scene) so a long scene does not repeat the same picture.

| Tier | Coverage | Images (approx.) |
|---|---|---|
| A: Launch | Story covers (about 450) plus scenes for the top 120 stories plus an atmospheric library (about 300, keyed by book, region and genre) | **about 1,200** |
| B: Full narrative | All narrative stories at pericope level | **about 3,000** |
| C: Ceiling | Every pericope in the Bible with variants | **about 6,000** |

Poetry, Wisdom and Epistles mostly draw from the **atmospheric library** (Priority 4) and from **historical-context** images (Priority 2), such as a first-century Ephesus street for Ephesians. This keeps the number of images low and avoids inventing events.

### 6.2 Pipeline

```
 (1) Plan         (2) Brief          (3) Generate      (4) Auto-validate      (5) Human review     (6) Publish
 Scene list  ──►  Scene brief   ──►  4 candidates ──► Vision-LLM checklist ─► Editor approves ──► Variants → R2/CDN
 from stories     (LLM drafts,       per scene        + safety + OCR text    / rejects / edits    pack manifest bump
 & pericopes      editor can edit)   (batch job)      check → score          prompt → regenerate
```

1. **Plan.** The admin platform generates the scene list from stories and pericopes, each with a *depiction priority* (1 Direct, 2 Historical, 3 Symbolic, 4 Atmospheric) proposed by a model and confirmed by an editor.
2. **Scene brief.** This is a structured, versioned document, not a free-form prompt:
   - passage reference, plus the passage text in KJV (public domain) used only as the model's input
   - period (e.g. "Herodian Judea, c. 6-4 BC"), region, setting (from the Place entity), and the confidence of each detail
   - **must show**, **must not show** (modern objects, text or lettering, halos unless iconographic style is chosen, invented characters), and **uncertain details to keep vague** (e.g. the Magi's number is not stated in the text, so do not show exactly three as fact)
   - a style preset: `story-cinematic` (warm, painterly, like your Story Mode reference) or `regular-atmospheric` (landscape, natural light, no figures)
   - representation guidance: **people of the ancient Near East and Mediterranean with historically plausible appearance and dress.** Note that the figures in the attached reference read as European. Our briefs should correct this.
   - composition: portrait 9:16, with a calm, low-detail **text-safe zone** in the lower 45%
3. **Generate.** Run batch jobs (nightly or on demand) through a provider adapter so that the model can be swapped. Store the seed, model, version and prompt for reproducibility.
4. **Automatic validation.** A vision-capable LLM scores each candidate against the brief's checklist: anachronisms, text in the image (plus OCR as a hard filter), anatomy and quality artefacts, nudity and violence level, symbolic-versus-literal labelling, representation, and fit with the text-safe zone. Low scores are discarded automatically. The rest are ranked.
5. **Human review is mandatory.** No image is published without an editor's approval. The review screen shows the passage, the brief and the candidates side by side with "approve", "reject (with reason)", "edit brief and regenerate" and "crop/focal point" (§9).
6. **Publish.** The worker produces the variants (§6.4), computes the legibility measurements (§4.3) and a blurhash, uploads the images to file storage (R2 or the VPS) under content-addressed names (`img/<sha256>.<w>.avif`), and updates the content manifest. Replacing an image is just a new hash and a new manifest version. Old files are garbage-collected after 90 days.

### 6.3 Image provider options

| Option | Quality | Cost per image* | Control | Notes |
|---|---|---|---|---|
| Hosted frontier models (Google Imagen/Gemini image, OpenAI gpt-image) | Very high | about $0.04-0.19 | Prompt only | Easiest. Check licence and content policy for religious figures |
| Hosted FLUX-class models via an API (e.g. BFL, Replicate, fal) | High | about $0.03-0.06 | Prompt plus LoRA/style | A good default for a consistent house style |
| Self-hosted open weights on rented GPUs | High | about $0.005-0.02 at batch scale | Full (LoRA, ControlNet) | Most work to run. Worth it only beyond about 20k generations |

\*Indicative late-2026 list prices. Confirm before budgeting. **Recommendation:** start with a hosted FLUX-class model plus a house-style LoRA trained on 30 to 50 approved images, using the provider adapter. Use a frontier model for covers if quality requires it.

Images are presented as **illustrations**. Long-press → "About this image" shows "AI-generated illustration · Depiction: Historical context · Reviewed by our editors", plus the scene note. No image ever contains text.

### 6.4 Storage, delivery and display

- **Variants:** AVIF (with WebP fallback for older Android) at 360×640 (thumbnail and preview), 720×1280 (default phone) and 1080×1920 (large phones and tablets), plus a **blurhash** string in the content database for instant placeholders.
- **Delivery:** Cloudflare CDN on a custom domain (e.g. `cdn.yourdomain`). Filenames are content-addressed, so `Cache-Control: immutable` lets the CDN cache them indefinitely and the origin is rarely touched. **Recommended origin: Cloudflare R2** (about 15 GB, free egress, survives a VPS failure). **VPS-only alternative:** serve the same files from the VPS with Caddy behind the CDN. This works well because almost all requests are cache hits, but it uses VPS disk and must be included in backups.
- **On the device (Coil 3):** a memory cache sized by device class, and a disk cache of 250 MB by default (adjustable, LRU). **Images in installed packs** live outside the cache and are never evicted.
- **Preloading:** decode N±1 at full size and prefetch N+2 and N+3 to disk only. On a metered or slow network, prefetch only N+1 and use the 360p variant.
- **Never blocks reading:** the render order is blurhash → cached image → network image. If the image is missing, the card falls back to an atmospheric image for the book and genre. If that is also missing, the card uses a colour field. Text renders first, every time.

### 6.5 Image packs (offline)

Packs are per story collection (e.g. "Life of Jesus", about 220 images, about 22 MB at 720p) and per book group. Packs are optional and their size is shown before download. Text-only reading needs no image download at all.

---

## 7. Learning system (brief item 5)

### 7.1 Question model

```
QuestionBank(id, storyId?, passage?, version, status)
Question ─ id, bankId, type, prompt, payload(json), answer(json),
           explanation: EditorialBlock, evidence: Passage[] (required, ≥1),
           difficulty (1-3), tags[], status, version, aiAssisted, reviewerId
types: MCQ | TRUE_FALSE | CLOZE | ORDERING | WHO_SAID | REF_MATCH | REFLECTION
```

- **Evidence is required.** The admin platform will not publish a question unless its answer is supported by at least one linked passage that an editor has confirmed.
- **Translation-safe cloze questions.** Fill-in-the-blank and ordering questions are stored as *verse plus word-selection rule* (e.g. "hide nouns and verbs from Luke 2:11"). The actual blanks are computed at runtime from the reader's chosen translation, so a KJV reader never gets an NCV word as the answer.
- **"Who said this?"** uses a speaker annotation table (`speakerId` per verse span), so the quote shown is always from the reader's translation.

### 7.2 AI assistance with validation

1. **Draft:** an LLM (batch) drafts 8 to 12 questions per story from the passage text, plus explanations with citations.
2. **Automatic checks:**
   - Each reference parses, exists, and lies within the story's passages or related passages.
   - **Grounding check:** a second model call gets only the cited verses and must reach the same answer without seeing the draft's answer. If the answers differ, the question is flagged.
   - Distractors must be wrong according to the text. For example, "Nazareth" as a distractor for "Where was Jesus born?" is fine, but the explanation must not wrongly claim Jesus never lived there.
   - Interpretive language ("this shows that God…") is classified as **interpretation** and flagged for theological review, separate from **direct statement** ("Luke 2:4-7 says…").
3. **Human review:** an editor approves, edits or rejects each question, and a second reviewer is needed for interpretive explanations.
4. **Publish:** the question bank is versioned and shipped in the story pack.

**Nothing is generated live on a user's device**, so the app never shows a user unreviewed theological explanations.

### 7.3 Memorization

- **Add to Memorize** from any card or verse selection. Users can organize verses into collections ("Psalm 23", "Comfort", "Family verses").
- A **progressive ladder** for each verse: Read → Read aloud (optional) → First letters only → Progressive hiding (25%, 50%, 75%, 100%) → Cloze → Recite reference → Recall from reference.
- **Scheduling:** the **FSRS** algorithm, an open-source modern spaced-repetition scheduler, runs entirely on the device. Each attempt is rated Again / Hard / Good / Easy, inferred from accuracy and hint use so users are not asked to rate themselves.
- **Reminders:** one optional daily notification at the user's chosen time, *only if* something is due. The wording is not pushy: "3 verses are ready to review".
- Story quizzes feed the same scheduler, so verses a user missed come back later as gentle review items ("Intelligent Scripture memory", your idea 3).

### 7.4 Progress tracking (encouraging, never competitive)

- Per story: read / partly read, quiz attempted, verses learned.
- Per verse: the FSRS state (stability, difficulty, due date) and a "learned" milestone.
- "Passages to revisit" is built from questions answered wrong more than once.
- **Not included:** leaderboards, points, public profiles, loss-framed streaks or notification storms. A soft "days you read this month" calendar replaces streaks. Missing a day never resets anything.

---

## 8. Offline architecture (brief item 6)

### 8.1 Databases on the device (SQLDelight, SQLite)

| File | Mode | Contents | Updated by |
|---|---|---|---|
| `bible-<tr>-v<n>.db` | Read-only | `verse(id, text, flags)`, `boundary(verseId, weight)`, `footnote`, `versification_map`, **FTS5** search index | Translation pack |
| `content-v<n>.db` | Read-only | stories, steps, accounts, scenes, image refs (hash, blurhash, legibility measurements), editorial blocks, questions, plans, characters, places, cross-refs, taxonomy | Content pack (delta or full) |
| `user.db` | Read/write | anchors, bookmarks, highlights, notes, plan enrolments and completions, story progress, quiz attempts, memory items and FSRS state, preferences, **change_log** | User actions and sync |

Why split them: content updates swap a file atomically and never risk user data, read-only databases can be memory-mapped and are fast, and sync only covers `user.db`.

**Room KMP vs SQLDelight.** Both are now viable on KMP. SQLDelight is recommended because it gives SQL-first control over FTS5, attaching read-only databases with `ATTACH`, and exact query plans for feed windows.

### 8.2 Content packs

```
manifest.json (signed)
{
  "schema": 3, "published": "2026-11-19T00:00Z",
  "packs": [
    {"id":"bible-kjv", "kind":"translation", "version":12, "bytes":4.6e6,
     "url":"…/bible-kjv-12.db.zst", "sha256":"…", "requires":[]},
    {"id":"content-core", "kind":"content", "version":41, "delta_from":[39,40], …},
    {"id":"img-life-of-jesus-720", "kind":"images", "version":7, "files":220, "bytes":2.2e7, …}
  ]
}
```

- **Compression:** zstd for databases (a full Bible is about 4-5 MB per translation). Image packs are a list of content-addressed files, so any image already cached is skipped.
- **Resumable:** HTTP range requests. Progress is saved per file. Downloads run in the background (Android WorkManager, iOS `URLSession` background transfers).
- **Integrity:** SHA-256 per file. The manifest is signed with Ed25519 and the public key is pinned in the app.
- **Versioning:** a content pack declares the minimum app schema it needs. Deltas are SQL patch files for small edits, and a full replacement is used beyond a size threshold.
- **Storage screen:** shows each installed pack with its size and a remove button, the cache size with a clear button, and a "keep images for stories I've started" toggle.
- **First run:** the app bundles the **KJV text, the content-core database (text only, about 3 MB), and about 30 atmospheric images** inside the app, so it is usable offline from the very first launch.

### 8.3 Feed performance (Compose Multiplatform)

- `VerticalPager` from `compose.foundation` (available on Android and iOS in CMP), with `beyondViewportPageCount = 1` and a stable `key` (startVerseId).
- **Window loader rather than Paging 3:** the grouper computes card boundaries for the whole source quickly (a book at most). The UI asks for card `i` and gets it from an LRU window of ±10 cards loaded with one SQL query each. This is simpler than Paging 3 with the same memory use. Paging 3 KMP stays an option if profiling says otherwise.
- **Recomposition hygiene:** immutable `@Immutable CardUi` models, `derivedStateOf` for chrome visibility, text measured once per card, and images loaded with a fixed size (no layout jumps).
- **iOS:** a spike in the first phase must confirm that fling physics, text selection, VoiceOver focus and 120 Hz smoothness feel native. This is the main technical risk (§11).

### 8.4 Synchronization (signed-in users)

- Every user-data write is appended to `change_log(entityType, entityId, op, fields, hlc, deviceId)`. HLC is a hybrid logical clock.
- **Push:** a batch is sent when the app goes to the background or the network returns, and every 10 minutes while the app is in use.
- **Pull:** `GET /sync?since=<cursor>` returns changes from other devices.
- **Merge rules by type:**

| Data | Rule |
|---|---|
| Bookmarks, highlights, memory items, plan enrolments | Add-wins set with tombstones (union of everything) |
| Notes | Last writer wins **per note**. If two versions were edited within a short time on different devices, keep both and show "Conflicting copies" in Notes |
| Reading anchor, preferences | Last writer wins (HLC) |
| Plan day completions, story progress, quiz attempts | Append-only. Progress is the union |
| FSRS state | Replay review events in HLC order (deterministic) |

Nothing is ever deleted silently.

---

## 9. Account architecture (brief item 7)

- **Guest by default.** On first launch the app creates a local `userId` (UUID). There is no sign-up screen and no network request.
- **Optional sign-in** from the Me tab: **Sign in with Apple** (required on iOS if any third-party sign-in is offered), **Google**, and **email magic link**. Passwords are not recommended at launch.
- **Implementation:** the Ktor API verifies Apple and Google ID tokens itself (their public signing keys) and sends email magic links through a transactional email provider (e.g. Postmark, Resend or Amazon SES; sending mail directly from a VPS often lands in spam). It then issues its own short-lived access token (JWT) and a rotating refresh token. Tokens are stored in the Keychain on iOS and the Keystore on Android.
- **Server data model:** Postgres tables that mirror `user.db`, keyed by `user_id`. Every query in the API is scoped to the signed-in user, with Postgres row-level security as a second safeguard, plus a `sync_cursor`.
- **Guest → account migration:**
  1. The user signs in. The app checks whether the account already has data on the server.
  2. If there is none, all local data is uploaded under the new user ID.
  3. If there is some, the app shows "**Combine** this phone's progress with your account?". Combining uses the merge rules in §8.4. "Keep separate" keeps this device as a guest. The app **never** offers "discard".
  4. A local backup of `user.db` is taken before the merge, so the merge can be undone.
- **Sign-out** keeps local data by default (with an option to remove it from this device).
- **Delete account** removes server data within 30 days and keeps local data. It is required by both app stores.
- **Privacy:** no third-party analytics SDKs. Crash reporting is opt-in. Usage metrics are anonymous and aggregated (or turned off).

---

## 10. Admin platform changes (brief item 8)

The admin platform is built new, as a Next.js web app hosted on your VPS and calling the Ktor admin API (§3.6). Admins sign in with a password plus a TOTP authenticator app, roles are enforced in the API, and every write goes to an audit log.

### 10.1 New modules

| Module | Screens and workflows |
|---|---|
| **Story Studio** | Story list (filters: status, testament, category, reviewer). Story editor: title, intro, context, steps (drag to reorder), accounts per step with a **reference picker** that validates ranges against the Bible database, related passages, characters and places, categories. **Live preview** using the mobile card renderer (web build of the CMP feed, or a faithful HTML renderer). Version diff and publish / archive |
| **Catalogue Import** | One-off import of the CSV. A clustering assistant proposes merges ("Feeding the five thousand: merge 3 rows into 1 story with 3 accounts?"). Accept or reject |
| **Scene & Image Studio** | Scene list per story or book, coloured by status (none, generating, needs review, approved). Brief editor. Batch generate with a **cost estimate before running**. Review grid: candidates next to the passage text and checklist scores, approve / reject with a reason / regenerate. Focal point and crop tool, preview of the text-safe zone with real Scripture overlaid in light and dark mode. Replace a published image. Variant status. Cost dashboard by month, model and scene |
| **Learning Studio** | Generate a draft question bank for a story, grounding-check results, edit, link evidence, approve. Memory-verse suggestions per story. Report queue for "This question seems wrong" sent from the app |
| **Editorial Review Queue** | One inbox for every item in `in_review`: summaries, context, questions, images, plans. Two-person approval for interpretive content. Reviewer comments |
| **Plans** | Plan builder (days → canonical ranges), templates (1 year, NT in 90 days, chronological, Psalms and Proverbs, Gospels, Beginner, topical), and a preview calendar |
| **Packs & Releases** | Build the content database from published versions → diff → sign manifest → staged release (internal, then 10%, then 100%) → **rollback** |
| **Translations** | Import a translation (USFM/OSIS), verify the verse count against the canonical scheme, map versification, compute boundaries, and record **licence metadata** (allowed: offline / audio / share limit / required attribution text) that the app enforces |

### 10.2 Features from earlier requirements (built new)

| Module | Scope |
|---|---|
| Devotionals | Rich-text editor, Scripture references, optional scene image, schedule by date |
| Verse of the Day | Calendar of verses, linked to a story or scene image where one exists |
| Teachings and revelations | Long-form editorial articles with Scripture citations, labelled as editorial content |
| Push notifications | Broadcast and segmented campaigns (FCM/APNs) plus the app's local reminders (plan, memorization due). Guests receive broadcasts without an account |
| Scheduling and publication history | Every content type has draft → scheduled → published → archived states, with a full version history |
| Security and audit log | Role-based access, two-factor sign-in for admins, an append-only audit log that also records AI generation events (model, prompt hash, cost) |

### 10.3 Roles

Admin · Editor (writes) · Theological reviewer (approves interpretive content) · Image reviewer · Publisher (releases packs). Every AI-assisted item records `aiAssisted=true`, the model and the prompt version.

---

## 11. Backend and hosting on your VPS

### 11.1 Options for the self-hosted backend

| Option | What runs on the VPS | Pros | Cons |
|---|---|---|---|
| **A. Ktor API + PostgreSQL** (recommended) | Ktor (auth, sync, admin API), PostgreSQL, the pipeline worker, the Next.js admin, Caddy | Kotlin end to end: the app, API and worker share canonical IDs and sync models. Light (runs well in 4 GB RAM). No per-user fees at any scale | We write auth ourselves (Apple/Google token checks, magic links, refresh tokens). This is well-trodden, about 1-2 weeks of work |
| B. Self-hosted Supabase | About 10 containers (Postgres, GoTrue auth, PostgREST, Realtime, Storage, Studio, Kong…) | Auth and an admin database UI come ready-made | Heavy (needs 8 GB+ RAM), many moving parts to upgrade and secure, and sync logic ends up in SQL functions |
| C. PocketBase or Appwrite | One self-hosted service | Very quick to start | Not Kotlin. Harder to fit the custom sync merge rules. Smaller ecosystem for the admin features we need |

**Recommendation: A.** The sync API sits behind a thin interface in the app, so the backend could move later without an app rewrite.

### 11.2 Deployment layout

```
                  Cloudflare (DNS, free CDN, DDoS protection)
                 ┌───────────────┴────────────────┐
     cdn.<domain> (packs, images)        api.<domain>, admin.<domain>
                 │                                 │
        R2 bucket (recommended)              Your VPS ─ Docker Compose
        or the VPS's /srv/packs              ├── caddy        (HTTPS, reverse proxy)
                                             ├── api          (Ktor)
                                             ├── admin        (Next.js)
                                             ├── worker       (image + LLM batch jobs, pack builder)
                                             ├── postgres     (PostgreSQL 16)
                                             └── backup       (WAL-G → off-site object storage)
```

- **Off-site backups are essential.** Continuous Postgres WAL archiving plus nightly full backups go to object storage outside the VPS (R2, Backblaze B2 or another provider), with **monthly restore drills**. Because content packs and images are content-addressed and also kept in the repository's build outputs or R2, the VPS can be rebuilt from scratch with `docker compose up` plus a database restore.
- **Security baseline:** SSH keys only, firewall (only 80/443 and SSH open), automatic security updates, fail2ban, Postgres not exposed to the internet, secrets in an `.env` file outside the repository, the admin site behind Cloudflare Access or an IP allowlist in addition to its own sign-in.
- **Monitoring:** Uptime Kuma (self-hosted) for availability, GlitchTip (self-hosted, Sentry-compatible) or Sentry's free tier for crashes and API errors, and disk/CPU alerts.
- **Resilience:** reading, Story Mode, plans and learning all work from the device and the CDN. If the VPS goes down, users lose only sign-in and sync until it is back. Changes queue on the device.

### 11.3 VPS sizing

| Scale | Signed-in users (about 30%) | Suggested VPS | Notes |
|---|---|---|---|
| Up to 10k MAU | Up to 3k | 2 vCPU, 4 GB RAM, 80 GB SSD | Everything on one box |
| Up to 100k MAU | Up to 30k | 4 vCPU, 8 GB RAM, 160 GB SSD | Still one box. Watch Postgres memory |
| Up to 1M MAU | Up to 300k | 8 vCPU, 16-32 GB RAM, or two servers (app + database) | Add a second server as a database replica or standby. Peak sync traffic is a few hundred requests per second |

What spec is your VPS? If it is smaller than the first row, it still works for development and the beta.

---

## 12. Cost analysis (brief item 9)

### 12.1 Assumptions

- Monthly active users (MAU) at 1k, 10k, 100k and 1M. About **30% sign in** (sync costs scale with these users only).
- Image bandwidth: an average of about **25 MB per MAU per month**. That is about 80 unique 720p images while reading online (about 90 KB each, many reused across cards in the same scene) plus occasional pack downloads. Text is tiny.
- Prices are list prices as of late 2026 and should be confirmed before committing. The Claude prices come from Anthropic's current price list. Other prices are approximate.

### 12.2 One-off content production

| Item | Basis | Estimate |
|---|---|---|
| Image generation, Tier B (3,000 approved images) | 4 candidates each plus 30% regeneration → about 15,600 generations × $0.03-0.10 | **$470 - $1,560** |
| Automatic image validation | About 15,600 vision checks with Claude Sonnet 5.5 ($2 / $10 per million input / output tokens; about 3k tokens in and 400 out each, so about $0.01; Batch API −50%) | **$80 - $160** |
| Story drafts (summaries, context, questions) | About 450 stories × (draft plus grounding check) with Claude Opus 5.5 ($4 / $20 per million) ≈ $0.35 per story; Batch API −50% | **$80 - $160** |
| Catalogue clustering and scene planning | One-off LLM pass | **< $50** |
| Style LoRA training (optional) | A few GPU hours | **$20 - $100** |
| **AI total** |  | **about $0.7k - $1.9k** |
| **Human editorial review (the real cost)** | About 15,600 image candidates at roughly 10 seconds each plus 3,000 detailed approvals (about 70 h), plus 450 stories × about 45 min (about 340 h) | **about 400 - 450 hours** |

Tier A alone (1,200 images, 120 stories) costs about a third of this.

### 12.3 Running costs per month

| | 1k MAU | 10k MAU | 100k MAU | 1M MAU |
|---|---|---|---|---|
| Image/text bandwidth | 25 GB | 250 GB | 2.5 TB | 25 TB |
| **Cloudflare CDN + R2** (free egress; storage about 15 GB; operation fees) | $0 | about $1 | about $5 | about $15-50 |
| *Same traffic on AWS S3 + CloudFront (for comparison)* | *about $2* | *about $21* | *about $210* | *about $2,000* |
| **Your VPS** (API, database, admin, worker) | Your existing server | Your existing server | Possibly an upgrade (§11.3) | A larger server or a second one, typically about $50-200 |
| Off-site database backups | about $0-1 | about $1 | about $2-5 | about $10-20 |
| Email (magic links, transactional provider) | $0 | about $0-15 | about $15-20 | about $50-100 |
| Crash reporting / monitoring | $0 (self-hosted) | $0 | $0-26 | about $26-80 |
| Push (FCM / APNs) | $0 | $0 | $0 | $0 |
| **Total per month, on top of the VPS** | **about $0-5** | **about $5-20** | **about $25-60** | **about $100-250, plus server upgrades** |

**Fixed costs:** Apple Developer Program $99/year, Google Play $25 once, a domain about $15/year, and CI minutes (about $0-50/month, mostly macOS runners for iOS builds). There are **no translation licence fees at launch** with KJV only.

**Ongoing content:** about $50-150 a month in AI credits for regenerations and new stories, plus editor time.

Self-hosting removes the per-user auth fees a managed backend would charge (with Supabase, about $700-1,500 a month at 1M MAU). The trade-off is your time running the server: updates, backups and monitoring.

**Cost controls built into the design:** images are generated once and reused for every user, content-addressed files never need re-downloading, the CDN absorbs almost all traffic and R2 has no egress charges, text-only mode is first-class, devices on metered networks prefetch less, and every batch job has a budget and shows its cost before it runs.

---

## 13. Implementation roadmap (brief item 10)

The durations assume **two mobile engineers, one backend/admin engineer, one part-time designer and one or two content editors**. Scale them if your team is different.

| Phase | Weeks | Scope | Depends on | Acceptance criteria |
|---|---|---|---|---|
| **0. Foundations** | 1-4 | **Project setup:** monorepo, KMP/CMP skeleton for Android and iOS, CI (build, tests, iOS on macOS runners), **VPS provisioning** (Docker Compose, Caddy, Postgres, off-site backups, monitoring), staging and production environments, Cloudflare DNS/CDN and R2 bucket, signing keys. Canonical ID and versification. KJV import with boundary weights. **CMP pager spike on iOS and Android** (fling, VoiceOver, text selection, 120 Hz). Design system and screen concepts for both modes. Imagery policy drafted | — | CI builds both apps on every PR and deploys the API to staging on the VPS. A database restore from backup has been tested. The spike holds 60/120 fps on a mid-range Android phone (e.g. Pixel 6a class) and an iPhone 12 with images. VoiceOver/TalkBack read card text in order. KJV verse count matches the canonical scheme (31,102 verses) |
| **1. Feed Engine + Free reading** | 5-10 | FeedEngine, Grouper (all densities and Auto), CanonicalSource, card UI, legibility engine, Aa settings, Jump-to, Classic reader, verse actions (copy, highlight, bookmark, note), `user.db`, position restore, text-only mode, bundled KJV | 0 | A full Bible read offline in feed and Classic modes. Golden tests pass for grouping. Cold start to last position in < 1.5 s. No sentence breaks at weight 0 except the documented long-sentence cases |
| **2. Admin foundation + content pipeline v1** (parallel with 1) | 5-12 | Ktor API skeleton and Postgres schema, admin app shell (Next.js, admin sign-in with TOTP, roles, audit log), catalogue import and clustering, Story Studio, review queue, pack builder and signed manifest | 0 | Admins sign in with roles enforced. 120 Tier-A stories drafted. A pack builds, is signed, and is installed by a test app |
| **3. Story Mode** | 11-16 | Discovery (shelves, search, journey), StorySource, interludes, account switcher, related passages, Continue Story, story packs | 1, 2 | Published stories open offline. Every card has a tappable reference into its chapter context. Parallels switch without losing the step |
| **4. Image pipeline** (parallel from week 8) | 8-16 | Scene planner, brief editor, provider adapter, batch generation, automatic validation, Image Studio review, variants, blurhash, image packs, cost dashboard | 2 | 1,200 Tier-A images approved. Feed p95 time to image on 4G < 300 ms for a preloaded card. Zero images with text in them |
| **5. Reading plans** | 14-17 | Seven plan templates plus topical, PlanSource, start date, catch-up, multiple plans, reminders | 1 | All plans work offline. Catch-up re-plans without losing completions |
| **6. Learning** | 16-21 | Question types, Learning Studio with grounding checks, quiz interludes, memorization ladder, FSRS, review reminders, "passages to revisit" | 3 | Every published question has evidence. All seven types pass accessibility checks. FSRS schedules correctly across restarts |
| **7. Accounts and sync** | 19-23 | Sign-in in the Ktor API (Apple and Google token checks, email magic link, refresh tokens), change log, push/pull, merge, guest migration with undo, delete account | 1, 5, 6 | Two-device conflict test suite passes. Guest migration loses nothing in 500 randomized trials |
| **8. Admin completion + devotional features** | 20-26 | Plans builder, staged releases and rollback, translation licence metadata, audit of AI events. **Devotionals, Verse of the Day, teachings, push campaigns and scheduling** in admin, and their screens on Home in the app | 2 | Content can be rolled back in < 5 minutes. Every AI item is traceable to its model and prompt. A devotional scheduled in admin appears on Home on its date, offline after sync |
| **9. Hardening and release** | 26-30 | Accessibility audit (WCAG 2.2 AA), performance on low-end devices, offline and poor-network testing, security review, privacy labels, store submission | All | Crash-free sessions > 99.5%. Peak memory < 300 MB on mid-range Android. Accessibility audit passes |

**Total:** about 30 weeks from an empty repository to launch with KJV. Building from scratch adds about a week of setup to Phase 0 and the admin shell to Phase 2, and the devotional features move into Phase 8. If you want an earlier public milestone, a **reading-only beta** (Phases 0, 1 and 3 with atmospheric images) is realistic at about week 16. The order is changed slightly from the brief: **free reading ships first**, because it is the simplest Feed Source and proves the shared engine before Story Mode is built on it. The image pipeline runs **in parallel** and uses placeholder and atmospheric images until it is ready, as your brief suggests.

### Top risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| KJV's archaic language puts off the new readers the brief targets | Medium | Medium | Story intros, summaries and an archaic-word glossary. The translation layer allows a modern translation to be added later as a content pack |
| Greenfield scope (app, admin and backend all at once) stretches a small team | Medium | High | The reading-only beta milestone. Static packs keep the API small (only auth, sync and admin). Admin starts with only Story Studio and the review queue |
| CMP on iOS feels non-native (scrolling, text selection, VoiceOver) | Medium | High | Phase 0 spike. Native UIKit interop for text selection if needed |
| AI images are inaccurate or theologically sensitive (depicting God, Jesus' face, violence, nudity) | High | High | Briefs with "must not show", mandatory human review, a "landscapes only" user preference, a written imagery policy approved by your reviewers |
| The editorial review backlog slows content | High | Medium | Launch with Tier A only. Batch review UI. Recruit volunteer reviewers with two-person approval |
| Story or quiz explanations stray into one denomination's doctrine | Medium | High | Separate "direct statement" from "interpretation". Two-person review. Prefer describing what the text says |
| Single VPS fails or is compromised | Low-medium | Medium (reading still works) | Off-site backups with tested restores, rebuild-from-scratch deploy scripts, security baseline (§11.2), a database standby at larger scale |
| Sync bugs lose notes | Low | Very high | Append-only change log, local backups before merge, a randomized conflict test suite |
| Feed scope creeps toward engagement mechanics | Medium | Medium | The guardrails in this document act as acceptance criteria. No infinite autoplay and no algorithmic feed of unrelated passages |

---

## 14. Additional recommendations (brief item 11)

### 14.1 Things not yet covered in the brief

1. **An imagery policy document**, approved before any generation, covering: depicting God the Father (recommend light or symbolism only), Jesus' face (style choices), crucifixion and violence (reverent, not graphic), Eden and nudity, angels (avoid fantasy tropes), and ethnic representation. It should also give users a **"Landscapes and settings only"** preference, which also suits traditions that avoid figurative images.
2. **A doctrinal neutrality statement.** Decide whether summaries aim at broad, non-denominational explanation, and who has the final say. Put this on an "Our approach" page in the app.
3. **Versification and canonical IDs** (§5.1). This is easy to get wrong and expensive to fix later.
4. **Translation licence metadata enforced in code** (offline allowed? audio allowed? share limits? attribution text?).
5. **Our own section headings** rather than the publishers' (they are copyrighted).
6. **Children and families.** The app will attract children. That brings COPPA/GDPR-K duties. A family mode would use gentler imagery, simpler questions and no account for under-13s.
7. **Data portability.** Export notes, highlights and memory verses as Markdown or JSON at any time.
8. **Search across the Bible offline** with FTS5, including by reference ("jn 3 16") and by phrase.
9. **Content error reporting** from any card ("Report an issue with this image or explanation"), feeding the admin review queue.
10. **Localization-ready content model**, so editorial blocks can be translated later without changing the structure.

### 14.2 Evaluation of your four ideas and the brief's research list

| Feature | Biblical value | Effort | Infrastructure cost | Recommendation |
|---|---|---|---|---|
| **Explore the Context** (one-tap sheet: setting, people, places, related passages, cross-refs) | Very high | Medium (content-heavy) | Low | **Phase 3.** It is the main study feature and reuses story entities |
| **Intelligent Scripture memory** (FSRS) | High | Low-medium | None | **Phase 6** (already in the plan) |
| **Read Along** (audio plus highlighting) | High (accessibility, people who prefer listening) | Medium-high | Low with on-device TTS, medium with recorded audio | **After launch.** Start with **KJV on-device TTS** (free, offline, word timing available). Recorded narration needs audio licences and verse timing data |
| **Timeline and maps** | High | Medium | Low (offline vector map tiles or static illustrated maps) | **After launch.** Use the Place entity (coordinates plus certainty). Illustrated maps are a cheaper start than vector tiles |
| Parallel Gospel accounts | Very high | Low (built into the model) | None | **Phase 3** |
| Character profiles and relationships | High | Medium | None | After launch (data is collected in Phase 3) |
| Shareable Scripture cards | Medium | Low | None | Phase 3, with attribution and quotation limits |
| Bible study collections | Medium | Low | None | After launch |
| Low-pressure reading consistency | Medium | Low | None | Phase 5 (calendar, not streaks) |
| Personalized recommendations | Low-medium | Medium | Low | Only simple, explainable "next in this journey" suggestions. **No engagement-optimized ranking** |
| Scripture Q&A assistant | Risky | High | Ongoing cost per question | **Defer.** If built, it should only quote retrieved Scripture with references and refuse to speculate. Treat it as a separate, clearly labelled tool |
| Memorization challenges | Low-medium | Low | None | Only private, self-paced goals. **No public competition** |

### 14.3 Architecture concerns to watch

- **Do not let the feed become infinite.** Every feed source has an end (a chapter, a story or a plan day). Reaching the end shows a calm "You've finished" card, not more content.
- **Keep the grouper free of side effects and well tested.** Every feature depends on it.
- **Treat the content packs as the API.** If the app can render everything from packs, backend outages never affect reading.
- **Budget alarms** on every paid API key, and per-job cost caps in the worker.

---

## 15. Decisions needed from you

Already decided: **built from scratch**, **KJV only at launch**, **hosted on your VPS**.

1. **Your VPS spec and provider** (CPU, RAM, disk, monthly bandwidth), so I can confirm the sizing in §11.3.
2. **Where images and packs live:** Cloudflare R2 behind the CDN (recommended, about $0-5 a month), or the VPS behind the CDN (everything on your server).
3. **Backend:** Ktor + PostgreSQL on the VPS (recommended), or self-hosted Supabase.
4. **Imagery policy:** depiction of Jesus and of God, and whether to offer "landscapes only" from launch (recommended).
5. **Phase order:** free reading before Story Mode (recommended), or Story Mode first as in the brief.
6. **Approval to start Phase 0** (repository, VPS setup, KJV import and the pager spike).
