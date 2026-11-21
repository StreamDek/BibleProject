package app.bible.data.study

import app.bible.core.VerseId

/**
 * One element of an original-language word: a prefix, the root or a suffix in Hebrew ("בְּ/רֵאשִׁית" is the prefix
 * "in" plus the root "beginning"), or the whole word in Greek.
 */
data class WordPart(
    val text: String,
    /** STEP's disambiguated Strong's id, e.g. "H7225G"; null for punctuation without a tag. */
    val stepId: String?,
    /** The grammar code of this element, e.g. "HNcfsa" or "V-AAI-3S". */
    val grammar: String?,
    val isRoot: Boolean,
) {
    /** The traditional Strong's number (0 for STEP's own prefix and suffix numbers above Strong's range). */
    val num: Int get() = stepId?.let { StrongsNumber.parse(it) }?.takeIf { isTraditional(it) } ?: 0
}

internal fun isTraditional(num: Int): Boolean =
    if (StrongsNumber.isGreek(num)) (num - StrongsNumber.GREEK) in 1..5624 else num in 1..8674

/** One word of the Hebrew or Greek text behind a KJV verse (STEP Bible TAHOT / TAGNT). */
data class OriginalWord(
    val index: Int,
    val language: Language,
    /** The word as written, with Hebrew vowel points and cantillation; prefix and punctuation separators removed. */
    val text: String,
    val xlit: String,
    /** STEP's word-for-word English. Hebrew elements are separated by "/"; <angle brackets> mark words best left untranslated. */
    val english: String,
    val parts: List<WordPart>,
    /** Where the word is found: "" for the main text, otherwise the source's type code ("Q(K)", "N(K)O", ...). */
    val type: String,
    /** A variant that changes the meaning, as the source states it. */
    val note: String,
) {
    /** The element that carries the meaning (the Hebrew root in braces, or the Greek word). */
    val root: WordPart? get() = parts.firstOrNull { it.isRoot } ?: parts.firstOrNull { it.stepId != null }

    /** Every traditional Strong's number in the word (prefixes included where they have one). */
    val nums: List<Int> get() = parts.map { it.num }.filter { it > 0 }.distinct()

    /** English without STEP's segment slashes: "in/ beginning" → "in beginning". */
    val gloss: String get() = english.replace("/ ", " ").replace("/", " ").replace(Regex("\\s+"), " ").trim()

    /** A Qere reading: the scribes' correction of the written Hebrew (Ketiv). */
    val isQere: Boolean get() = type.startsWith("Q")

    /** The KJV's Greek (Textus Receptus) has a different word here; [note] gives it. */
    val traditionalDiffers: Boolean get() = "(K)" in type

    companion object {
        private val ID = Regex("[HG]\\d{4}[A-Za-z]?")

        /** Decodes one verse of study.db's orig_verse.words. */
        fun decodeVerse(lang: String, words: String): List<OriginalWord> {
            val language = Language.of(lang)
            return words.split('\u001e').mapIndexedNotNull { i, w ->
                val f = w.split('\u001f')
                if (f.size < 7) return@mapIndexedNotNull null
                val text = f[0]; val strongs = f[3]; val grammar = f[4]
                val hebrew = language != Language.Greek
                OriginalWord(
                    index = i,
                    language = if (hebrew && grammar.startsWith("A")) Language.Aramaic else language,
                    text = text.replace("/", "").replace("\\", ""),
                    xlit = f[1].replace("/", ""),
                    english = f[2],
                    parts = if (hebrew) hebrewParts(text, strongs, grammar) else greekParts(text, strongs, grammar),
                    type = f[5],
                    note = f[6],
                )
            }
        }

        /**
         * Hebrew elements: text "הַ/שָּׁמַ֖יִם", tags "H9009/{H8064}", grammar "HTd/Ncmpa". Punctuation after "\" in the
         * text and tags (e.g. "\׃" and "\H9016") is left out of the parts.
         */
        private fun hebrewParts(text: String, strongs: String, grammar: String): List<WordPart> {
            val texts = text.substringBefore('\\').split('/')
            val tags = strongs.substringBefore('\\').trimEnd('+').split('/')
            val lang = grammar.firstOrNull()?.toString().orEmpty()
            val codes = grammar.drop(1).split('/')
            return texts.indices.map { i ->
                val tag = tags.getOrNull(i).orEmpty()
                WordPart(
                    text = texts[i],
                    stepId = ID.find(tag)?.value,
                    grammar = codes.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { lang + it },
                    isRoot = '{' in tag,
                )
            }
        }

        /** Greek: one element, or several for a joined word ("G2532+G1473" with "CONJ+P-1NS"). */
        private fun greekParts(text: String, strongs: String, grammar: String): List<WordPart> {
            val tags = strongs.split('+').map { it.trim() }
            val codes = grammar.split('+').map { it.trim() }
            return tags.indices.map { i ->
                WordPart(text = if (i == 0) text else "", stepId = ID.find(tags[i])?.value, grammar = codes.getOrNull(i), isRoot = i == 0)
            }
        }
    }
}

/** The original text of one verse. */
data class OriginalVerse(val verse: VerseId, val language: Language, val words: List<OriginalWord>) {
    /** The STEP senses this verse uses for a Strong's number, in order of first use. */
    fun sensesOf(num: Int): List<String> = words.flatMap { w -> w.parts.filter { it.num == num }.mapNotNull { it.stepId } }.distinct()

    /** The words carrying a Strong's number, for picking out a KJV word's original. */
    fun wordsWith(num: Int): List<OriginalWord> = words.filter { w -> w.parts.any { it.num == num } }
}

/** A grammar code explained (STEP Bible TEHMC / TEGMC). */
data class MorphInfo(val code: String, val parsing: String, val summary: String?, val explanation: String?, val example: String?)

/** A person, place or other named thing (STEP Bible TIPNR). */
data class BibleEntity(
    val id: Int,
    val name: String,
    /** "person", "place" or "other". */
    val kind: String,
    /** "Male", "Female", "Group", "Place", "Supernatural", ... */
    val type: String,
    val description: String?,
    val summary: String?,
    val tribe: String?,
    val area: String?,
    val lat: Double?,
    val lon: Double?,
    val verses: Int,
    /** TIPNR's unique name, with the first reference and last book ("Abraham@Gen.11.26-1Pe"); tells namesakes apart. */
    val uniqueName: String,
)

/** One Hebrew or Greek form of an entity's name. */
data class EntityNameForm(val stepId: String, val significance: String, val original: String, val translated: String)

/** A cross-reference to a verse or range, with how many readers voted for it (OpenBible.info). */
data class CrossReference(val start: VerseId, val end: VerseId, val votes: Int)

/** STEP Bible's three-letter book codes ("Gen", "Sng", "Jhn", ...), in canonical order. */
object StepBooks {
    val codes = listOf(
        "Gen", "Exo", "Lev", "Num", "Deu", "Jos", "Jdg", "Rut", "1Sa", "2Sa", "1Ki", "2Ki", "1Ch", "2Ch", "Ezr", "Neh",
        "Est", "Job", "Psa", "Pro", "Ecc", "Sng", "Isa", "Jer", "Lam", "Ezk", "Dan", "Hos", "Jol", "Amo", "Oba", "Jon",
        "Mic", "Nam", "Hab", "Zep", "Hag", "Zec", "Mal", "Mat", "Mrk", "Luk", "Jhn", "Act", "Rom", "1Co", "2Co", "Gal",
        "Eph", "Php", "Col", "1Th", "2Th", "1Ti", "2Ti", "Tit", "Phm", "Heb", "Jas", "1Pe", "2Pe", "1Jn", "2Jn", "3Jn",
        "Jud", "Rev",
    )
    private val numbers = codes.withIndex().associate { (i, c) -> c to i + 1 }

    /** The canonical book number (1..66) for a STEP code, or null. */
    fun number(code: String): Int? = numbers[code]
}
