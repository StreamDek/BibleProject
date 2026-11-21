package app.bible.pipeline.study

import app.bible.core.Canon
import app.bible.core.VerseId
import java.io.File

/**
 * One word of the Hebrew or Greek text, as STEP Bible's TAHOT/TAGNT give it. Fields are kept as the source writes
 * them (only trimmed), so nothing is reinterpreted on the way into study.db:
 * - [text]: the word; in Hebrew "/" separates prefixes and suffixes from the root and "\" separates punctuation.
 * - [strongs]: dStrong tags; in Hebrew one per element, the root in {braces}.
 * - [grammar]: the grammar code (Hebrew: one per element after the language letter, e.g. "HC/Ncmsa").
 * - [type]: where the word is found (Hebrew L/Q/R..., Greek N/K/O...).
 * - [note]: a variant that changes the meaning, where the source gives one.
 */
data class OrigWord(
    val text: String,
    val xlit: String,
    val english: String,
    val strongs: String,
    val grammar: String,
    val type: String,
    val note: String,
)

class OrigStats {
    var rows = 0
    var kept = 0
    val skippedTypes = sortedMapOf<String, Int>()
    var titleWords = 0
    var flaggedTraditional = 0
    val badRefs = sortedSetOf<String>()
}

object OriginalTextParser {
    /** STEP Bible's book abbreviations, in canonical order (index + 1 = book number). */
    val BOOKS = app.bible.data.study.StepBooks.codes
    private val bookNumber = BOOKS.withIndex().associate { (i, b) -> b to i + 1 }

    /**
     * "Gen.1.1#01=L", "Psa.3.1(3.2)#01=L" (Hebrew numbering in brackets), "1Jn.2.14[2.13]#01=NKO" (KJV numbering in
     * square brackets), "2Co.1.7{1.6}#..." (other editions). The English/NRSV reference comes first.
     */
    private val REF = Regex("^([1-3A-Za-z]{3})\\.(\\d+)\\.(\\d+)((?:[\\[({][\\d.]+[\\])}])*)#(\\d+)=(\\S+)$")
    private val KJV_REF = Regex("\\[(\\d+)\\.(\\d+)]")

    data class Ref(val verse: VerseId?, val title: Boolean, val type: String)

    fun ref(field: String, stats: OrigStats): Ref? {
        val m = REF.matchEntire(field.trim()) ?: return null
        val (bookCode, ch, vs, extra, _, type) = m.destructured
        val book = bookNumber[bookCode] ?: run { stats.badRefs += field; return null }
        var chapter = ch.toInt(); var verse = vs.toInt()
        // The KJV's own numbering, where STEP records that it differs.
        KJV_REF.find(extra)?.let { k -> chapter = k.groupValues[1].toInt(); verse = k.groupValues[2].toInt() }
        if (verse == 0) return Ref(null, true, type) // a Psalm title (verse 0 in STEP's English numbering)
        val id = VerseId.of(book, chapter, verse)
        if (!Canon.isValid(id)) { stats.badRefs += field.substringBefore('#'); return Ref(null, false, type) }
        return Ref(id, false, type)
    }

    /**
     * TAHOT. Keeps the text translators use: Leningrad (L), the scribes' Qere corrections (Q) and restored text (R).
     * Words only in the Greek tradition (X) and other variants are counted, not shipped.
     */
    fun parseHebrew(files: List<File>, stats: OrigStats): Map<VerseId, List<OrigWord>> {
        val out = LinkedHashMap<VerseId, MutableList<OrigWord>>()
        for (f in files) f.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#' || line[0] == ' ' || line[0] == '\t') continue
                val c = line.trimEnd('\r').split('\t')
                if (c.size < 6 || '#' !in c[0] || '=' !in c[0]) continue
                val r = ref(c[0], stats) ?: continue
                stats.rows++
                val keep = r.type.first() in "LQR"
                if (!keep) { stats.skippedTypes.merge(r.type, 1, Int::plus); continue }
                if (r.title) { stats.titleWords++; continue }
                val id = r.verse ?: continue
                stats.kept++
                out.getOrPut(id) { ArrayList() } += OrigWord(
                    text = c[1].trim(), xlit = c[2].trim(), english = c[3].trim(),
                    strongs = c[4].trim(), grammar = c[5].trim(), type = r.type,
                    note = c.getOrNull(6)?.trim().orEmpty(),
                )
            }
        }
        return out
    }

    private val GREEK_WORD = Regex("^(.*?)\\s*\\(([^)]*)\\)\\s*$")

    /**
     * TAGNT. Keeps the words of the KJV's Greek (Scrivener's Textus Receptus): types with K or k. Where the
     * Traditional text has a different word that changes the translation ("N(K)O"), the Nestle-Aland word is kept and
     * the Traditional reading goes in [OrigWord.note], so the app can say so instead of hiding the difference.
     */
    fun parseGreek(files: List<File>, stats: OrigStats): Map<VerseId, List<OrigWord>> {
        val out = LinkedHashMap<VerseId, MutableList<OrigWord>>()
        for (f in files) f.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#' || line[0] == ' ' || line[0] == '\t') continue
                val c = line.trimEnd('\r').split('\t')
                if (c.size < 7 || '#' !in c[0] || '=' !in c[0]) continue
                val r = ref(c[0], stats) ?: continue
                stats.rows++
                val inKjvText = 'K' in r.type || 'k' in r.type
                if (!inKjvText) { stats.skippedTypes.merge(r.type, 1, Int::plus); continue }
                val id = r.verse ?: continue
                stats.kept++
                val traditionalDiffers = "(K)" in r.type
                if (traditionalDiffers) stats.flaggedTraditional++
                val w = GREEK_WORD.matchEntire(c[1].trim())
                val tag = c[3].trim()
                out.getOrPut(id) { ArrayList() } += OrigWord(
                    text = w?.groupValues?.get(1) ?: c[1].trim(),
                    xlit = w?.groupValues?.get(2).orEmpty(),
                    english = c[2].trim(),
                    // Usually "G0025=V-AAI-3S"; a joined word is "G2532=CONJ + G1473=P-1NS".
                    strongs = tag.split(" + ").joinToString("+") { it.substringBefore('=').trim() },
                    grammar = tag.split(" + ").joinToString("+") { it.substringAfter('=', "").trim() },
                    type = r.type,
                    note = if (traditionalDiffers) c[6].trim() else "",
                )
            }
        }
        return out
    }

    /** One verse's words for study.db: fields joined by U+001F, words by U+001E. */
    fun encode(words: List<OrigWord>): String = words.joinToString("\u001e") { w ->
        // The commonest types (in every edition) are left empty to keep the pack small.
        val type = if (w.type == "L" || w.type == "NKO") "" else w.type
        listOf(w.text, w.xlit, w.english, w.strongs, w.grammar, type, w.note).joinToString("\u001f") { it.replace('\u001e', ' ').replace('\u001f', ' ') }
    }

    /** The STEP ids of a Hebrew or Greek tag field: "H9003/{H7225G}\\H9016" → [H9003, H7225G, H9016]. */
    fun ids(strongs: String): List<String> = Regex("[HG]\\d{4}[A-Za-z]?").findAll(strongs).map { it.value }.toList()
}

/** A grammar code explained in words (TEHMC/TEGMC). */
data class MorphRecord(val code: String, val parsing: String, val summary: String?, val explanation: String?, val example: String?)

object MorphologyParser {
    private val CODE = Regex("^([A-Z][A-Za-z0-9\\-]*)\\t(Function=.*)$")

    /**
     * Blocks are a code line ("HVqp3ms<TAB>Function=Verb ; Stem=Qal ..."), then indented lines: a summary, a
     * plain-English explanation and an example, ended by "$".
     */
    fun parse(file: File): List<MorphRecord> {
        val out = ArrayList<MorphRecord>()
        var code: String? = null
        var parsing = ""
        val body = ArrayList<String>()
        fun flush() {
            val c = code ?: return
            val lines = body.map { it.trim().trim('"').trim() }.filter { it.isNotEmpty() }
            val example = lines.firstOrNull { it.startsWith("Example:") }?.removePrefix("Example:")?.trim()
                ?: lines.getOrNull(2)?.replace("_", "")?.trim()
            val ex = example?.substringBefore("\": \"")?.trim('"', ' ')
            out += MorphRecord(c, parsing.trim().replace(Regex("\\s+"), " "), lines.getOrNull(0), lines.getOrNull(1), ex?.takeIf { it.isNotEmpty() })
            code = null; body.clear()
        }
        file.bufferedReader().useLines { all ->
            for (raw in all) {
                val line = raw.trimEnd('\r').trimEnd('\t')
                val m = CODE.matchEntire(line)
                when {
                    m != null -> { flush(); code = m.groupValues[1]; parsing = m.groupValues[2] }
                    line.trim() == "$" -> flush()
                    code != null && (line.startsWith("\t") || line.startsWith(" ")) -> body += line
                }
            }
        }
        flush()
        return out.distinctBy { it.code }
    }

    /** The codes a word's grammar field uses: "HC/Ncmsa" → [HC, HNcmsa]; Greek codes are used whole. */
    fun codes(grammar: String, hebrew: Boolean): List<String> {
        if (grammar.isBlank()) return emptyList()
        if (!hebrew) return grammar.split('+').map { it.trim() }.filter { it.isNotEmpty() }
        val lang = grammar.first()
        return grammar.drop(1).split('/').filter { it.isNotBlank() }.map { "$lang$it" }
    }
}
