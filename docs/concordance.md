# Concordance and original-language study

How VerseUp's word study works, where its data comes from, and what is still to come. The build report for the
current pack is in [content/study/import-report.md](../content/study/import-report.md).

## Repo audit (before this work)

- Reading: KJV only (`bible-kjv.db`, built by `:pipeline:importKjv` from eBible.org's public-domain KJV). The feed,
  the classic reader and story reader all share `VerseActionsSheet` for verse actions.
- No lexicon, Strong's data, tagged text, morphology or concordance existed. Search was English full-text only.
- No reading plans exist yet, so the spec's reading-plan entry points are left for when plans ship.
- `user.db` held settings, highlights, bookmarks and notes (schema v1, no migrations).
- No attributions or licences page existed.

## Sources and licences

| Source | Used for | Licence | Notes |
| --- | --- | --- | --- |
| Strong's Hebrew Dictionary (openscriptures/strongs XML) | Hebrew and Aramaic entries, derivations | Public domain | Only Strong's own fields. The editors' outline, TWOT numbers and Greek equivalents are left out. |
| Strong's Greek Dictionary (Ulrik Petersen's XML, same repo) | Greek entries, derivations | Public domain ("Copy Freely") | |
| STEPBible TBESH (Tyndale House) | Hebrew senses: lemma, transliteration, gloss, grammar | CC BY 4.0 | The Meaning column is Online Bible's Abridged BDB, which STEP says needs Online Bible's permission. It is not shipped. |
| STEPBible TBESG (Tyndale House) | Greek senses with the corrected Abbott-Smith definitions | CC BY 4.0 | |
| CrossWire KJV module 3.1 | Strong's tags on each KJV word | "General public license to use this text for any purpose" | Its .conf also says DistributionLicense=GPL. See open questions. |
| Noto Serif Hebrew | Hebrew with vowel points and cantillation | SIL OFL 1.1 | Static Regular and SemiBold cut from the variable font. |

Excluded on purpose: STEP's TTESV (CC BY-NC and ESV text), TBESH meanings (see above). STEP asks that its files
not be reposted, so the pipeline downloads them at pinned commits instead of committing them.

Every credit shown in the app comes from `composeApp/src/commonMain/composeResources/files/attributions.json`
(Settings → About & Legal → Attributions & Licences, and the source line under each word study). The pipeline
refuses to build the pack if an entry is missing a required field or a licence.

## Pipeline

`./gradlew :pipeline:buildStudy` downloads each source at a pinned commit, checks its SHA-256, and writes:

- `composeResources/files/study.db` (about 27 MB, read-only SQLite, deterministic) and `study.stamp`.
- `content/study/import-report.md`: sources, counts, quarantined records and weakly matched verses.

Records that fail validation are quarantined and listed in the report, never silently dropped or guessed. CI
rebuilds the pack and fails if the stamp or report changes.

KJV tags are mapped onto VerseUp's KJV text word by word (longest common subsequence per verse): 99.94% of words
match. Tags that cannot be placed are left out and counted. The Greek article (G3588) and the Hebrew object marker
(H853) keep their occurrences, but are not credited with the English of a phrase they share with another word.

## Schema (study.db)

`strongs`, `strongs_ref` (derivation links), `step_entry` (STEP senses, kept separate from Strong's),
`kjv_tags` (per verse: start, end, numbers, morphology), `occurrence`, `rendering`, `english`, and `lexicon_fts`
(FTS4, since Android's SQLite has no FTS5) over ids, folded lemmas, transliterations, glosses and definitions.
Saved words and history live in `user.db` (`saved_word`, `study_history`, migration `1.sqm`).

## In the app (phase 1)

- Hold any verse, then tap a word under "Study a word" (KJV). Underlined words are tagged; untagged words explain
  why and offer an English search.
- The word sheet opens half height: headword, transliteration, Strong's number, short definition. Drag up or tap
  Explore for Strong's, STEP Bible or Compare (remembered), KJV renderings, verses per book, all occurrences
  (paged, by book), derivations, and save.
- Study page (Study in the reader's bar): search English, Hebrew, Greek (accents optional), transliterations,
  Strong's numbers (H430, g26, 1254), STEP senses (H1254A) and references, with filters and spelling suggestions.
  Recent, saved (star, note, filter) and suggested words.
- Accessibility: Hebrew is right to left, colour always comes with a text label, motion follows the system's
  reduce-motion setting, headings are marked for screen readers.

## Performance

Measured by `StudyPerformanceTest` on the JVM (median ms): word sheet data 6.7, verse tags 0.9, occurrences page
0.3, search 1.4 to 14. The test fails above a fifth of the on-device targets.

## Phases

1. **This PR:** data pack, Strong's and STEP lexicons, KJV word tags, word sheet, Study page, saved words and
   history, attributions page.
2. Original-language text (STEP TAHOT/TAGNT), full morphology (TEHMC/TEGMC), an interlinear view, and resolving
   which STEP sense each verse uses.
3. People and places (TIPNR, labelled where its descriptions are AI-written) linked to stories, frequency charts,
   cross-references, Psalm title tags.
4. Downloadable packs and storage settings, pronunciation audio, sync of saved words, optional AI help (always
   labelled and separate from the lexicons).

## Open questions for Henry

- CrossWire's KJV module says "general public license to use this text for any purpose" but its .conf lists
  DistributionLicense=GPL. The tags are facts about a public-domain text, but a written OK from CrossWire would
  settle it.
- Hebrew definitions beyond STEP's glosses need either Online Bible's permission (Abridged BDB) or another
  public-domain Hebrew lexicon.
