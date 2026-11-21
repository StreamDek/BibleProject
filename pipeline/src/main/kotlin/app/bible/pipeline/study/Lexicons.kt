package app.bible.pipeline.study

import app.bible.data.study.StrongsNumber
import java.io.File
import java.text.Normalizer
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader

/** A Strong's dictionary entry as parsed, before it is written to study.db. */
data class StrongsRecord(
    val num: Int,
    val lang: String,
    val lemma: String,
    val xlit: String?,
    val pron: String?,
    val pos: String?,
    val derivation: String?,
    val definition: String?,
    val kjvDef: String?,
    /** Entries named in the derivation (Strong's own cross-references). */
    val refs: Set<Int>,
    val sourceId: String,
)

/** A STEP Bible brief-lexicon row (one disambiguated sense). */
data class StepRecord(
    val id: String,
    val num: Int,
    val eStrong: String,
    val uStrong: String?,
    val label: String?,
    val lang: String,
    val lemma: String,
    val xlit: String?,
    val morph: String?,
    val gloss: String?,
    val meaning: String?,
    val sourceId: String,
)

/** Records that could not be imported, with why. They are listed in the import report, never dropped silently. */
class Quarantine {
    val items = ArrayList<String>()
    fun add(source: String, what: String) { items += "$source: $what" }
}

private fun xml(file: File): XMLStreamReader = XMLInputFactory.newInstance().apply {
    setProperty(XMLInputFactory.IS_COALESCING, true)
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
}.createXMLStreamReader(file.inputStream().buffered(), "UTF-8")

/** Unicode NFC and single spaces, nothing else changed. */
fun clean(s: String?): String? = s?.let { Normalizer.normalize(it, Normalizer.Form.NFC).replace(Regex("\\s+"), " ").trim() }?.takeIf { it.isNotEmpty() }

object StrongsHebrewParser {
    /**
     * Reads Open Scriptures' StrongHebrewG.xml (OSIS). Only Strong's own text is taken: the headword, its
     * transliteration, pronunciation and part of speech, and the three notes (derivation, definition, KJV
     * renderings). The outline lists, TWOT numbers and Greek equivalents in that file come from other works and
     * are left out (docs/concordance.md, licensing).
     */
    fun parse(file: File, sourceId: String, quarantine: Quarantine): List<StrongsRecord> {
        val r = xml(file)
        val out = ArrayList<StrongsRecord>()
        var inEntry = false
        var n = 0
        var head: Map<String, String>? = null
        var lang = "heb"
        var note: String? = null
        val text = StringBuilder()
        val notes = HashMap<String, String>()
        val refs = LinkedHashSet<Int>()
        var skipDepth = 0
        while (r.hasNext()) {
            when (r.next()) {
                START_ELEMENT -> {
                    val name = r.localName
                    if (skipDepth > 0) { skipDepth++; continue }
                    when {
                        name == "div" && r.attr("type") == "entry" -> {
                            inEntry = true; n = r.attr("n")?.toIntOrNull() ?: 0; head = null; notes.clear(); refs.clear(); lang = "heb"
                        }
                        !inEntry -> Unit
                        name == "foreign" || name == "list" -> skipDepth = 1
                        name == "w" && note == null && head == null -> {
                            head = (0 until r.attributeCount).associate { r.getAttributeLocalName(it) to r.getAttributeValue(it) }
                            lang = r.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang") ?: "heb"
                        }
                        name == "note" -> { note = r.attr("type"); text.setLength(0) }
                        name == "w" && note != null -> {
                            // A reference inside a note: <w src="433" lemma="..." xlit="..."/>
                            val src = r.attr("src")?.toIntOrNull()
                            if (src != null) {
                                refs += src
                                text.append("H").append(src)
                                r.attr("lemma")?.let { text.append(" (").append(it).append(")") }
                            } else r.attr("lemma")?.let { text.append(it) }
                        }
                    }
                }
                CHARACTERS -> if (skipDepth == 0 && note != null) text.append(r.text)
                END_ELEMENT -> {
                    if (skipDepth > 0) { skipDepth--; continue }
                    when (r.localName) {
                        "note" -> { note?.let { notes[it] = text.toString() }; note = null }
                        "div" -> if (inEntry) {
                            inEntry = false
                            val h = head
                            val lemma = clean(h?.get("lemma"))
                            if (n <= 0 || h == null || lemma == null) quarantine.add(sourceId, "entry n=$n has no headword")
                            else out += StrongsRecord(
                                num = n,
                                lang = if (lang == "arc") "arc" else "heb",
                                lemma = lemma,
                                xlit = clean(h["xlit"]),
                                pron = clean(h["POS"]),
                                pos = clean(h["morph"]),
                                derivation = clean(notes["exegesis"]),
                                definition = clean(notes["explanation"]),
                                kjvDef = clean(notes["translation"]),
                                refs = refs.toSet() - n,
                                sourceId = sourceId,
                            )
                        }
                    }
                }
            }
        }
        return out
    }
}

object StrongsGreekParser {
    /** Reads Ulrik Petersen's strongsgreek.xml (1.4). */
    fun parse(file: File, sourceId: String, quarantine: Quarantine): List<StrongsRecord> {
        val r = xml(file)
        val out = ArrayList<StrongsRecord>()
        var n = 0
        var lemma: String? = null
        var xlit: String? = null
        var pron: String? = null
        var field: String? = null
        val text = StringBuilder()
        val fields = HashMap<String, String>()
        val refs = LinkedHashSet<Int>()
        var inEntry = false
        while (r.hasNext()) {
            when (r.next()) {
                START_ELEMENT -> when (r.localName) {
                    "entry" -> {
                        inEntry = true; n = r.attr("strongs")?.toIntOrNull() ?: 0
                        lemma = null; xlit = null; pron = null; fields.clear(); refs.clear()
                    }
                    "greek" -> if (inEntry) {
                        if (field == null && lemma == null) { lemma = r.attr("unicode"); xlit = r.attr("translit") }
                        else if (field != null) text.append(r.attr("unicode") ?: "")
                    }
                    "pronunciation" -> if (inEntry && field == null && pron == null) pron = r.attr("strongs")
                    "strongs_derivation", "strongs_def", "kjv_def" -> if (inEntry) { field = r.localName; text.setLength(0) }
                    "strongsref" -> if (field != null) {
                        val s = r.attr("strongs")?.toIntOrNull()
                        val hebrew = r.attr("language").equals("HEBREW", ignoreCase = true)
                        if (s != null) {
                            if (!hebrew) refs += StrongsNumber.of('G', s) else refs += s
                            text.append(if (hebrew) "H" else "G").append(s)
                        }
                    }
                }
                CHARACTERS -> if (field != null) text.append(r.text)
                END_ELEMENT -> when (r.localName) {
                    "strongs_derivation", "strongs_def", "kjv_def" -> { field?.let { fields[it] = text.toString() }; field = null }
                    "entry" -> {
                        inEntry = false
                        val l = clean(lemma)
                        // Strong left G2717 and G3203-G3302 unused; those empty entries are expected.
                        if (n <= 0 || l == null) { if (n != 2717 && n !in 3203..3302) quarantine.add(sourceId, "entry strongs=$n has no Greek headword") }
                        else out += StrongsRecord(
                            num = StrongsNumber.of('G', n),
                            lang = "grc",
                            lemma = l,
                            xlit = clean(xlit),
                            pron = clean(pron),
                            pos = null,
                            derivation = clean(fields["strongs_derivation"]),
                            definition = clean(fields["strongs_def"]),
                            kjvDef = clean(fields["kjv_def"])?.removePrefix(":--")?.trim(),
                            refs = refs.toSet() - StrongsNumber.of('G', n),
                            sourceId = sourceId,
                        )
                    }
                }
            }
        }
        return out
    }
}

object StepLexiconParser {
    private val ROW = Regex("^([HG]\\d{4,5}[A-Za-z]?)\t([HG]\\d{4,5}[A-Za-z]?) *= *([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t?(.*)$")

    /**
     * Reads TBESH or TBESG. Rows are "eStrong, dStrong = label, uStrong, lemma, transliteration, morph, gloss,
     * meaning"; the long header and the example blocks inside it are skipped.
     *
     * [keepMeaning] is false for TBESH: its Meaning column is Online Bible's Abridged BDB, which STEP says needs
     * Online Bible's permission, so only STEP's own glosses are imported.
     */
    fun parse(file: File, sourceId: String, keepMeaning: Boolean, quarantine: Quarantine): List<StepRecord> {
        val out = ArrayList<StepRecord>()
        val seen = HashSet<String>()
        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trimEnd('\r', '\n')
                if (line.isEmpty() || !(line[0] == 'H' || line[0] == 'G') || !line[1].isDigit()) continue
                val m = ROW.matchEntire(line)
                if (m == null) { quarantine.add(sourceId, "unparsed row: ${line.take(80)}"); continue }
                val g = m.groupValues
                val dStrong = g[2]
                if (!seen.add(dStrong)) { quarantine.add(sourceId, "duplicate id $dStrong"); continue }
                val lang = when {
                    g[7].startsWith("A:") -> "arc"
                    dStrong.startsWith("G") -> "grc"
                    else -> "heb"
                }
                val base = StrongsNumber.parse(g[1])?.takeIf { isTraditional(it) } ?: 0
                val lemma = clean(g[5])
                if (lemma == null) { quarantine.add(sourceId, "$dStrong has no lemma"); continue }
                out += StepRecord(
                    id = dStrong,
                    num = base,
                    eStrong = g[1],
                    uStrong = clean(g[4])?.takeIf { it != dStrong },
                    label = clean(g[3]),
                    lang = lang,
                    lemma = lemma,
                    xlit = clean(g[6]),
                    morph = clean(g[7]),
                    gloss = clean(g[8]),
                    meaning = if (keepMeaning) meaningText(g[9]) else null,
                    sourceId = sourceId,
                )
            }
        }
        return out
    }

    /** Strong's own range: H1-H8674 and G1-G5624. Higher numbers are STEP or NASB extensions. */
    fun isTraditional(num: Int): Boolean =
        if (StrongsNumber.isGreek(num)) (num - StrongsNumber.GREEK) in 1..5624 else num in 1..8674

    /**
     * STEP's meaning HTML as plain text with **bold** marks and line breaks: <br> becomes a line break, <b> bold,
     * references and other tags keep only their text.
     */
    fun meaningText(html: String): String? {
        var s = html
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</?b>"), "**")
        s = s.replace(Regex("<[^>]*>"), "")
        s = s.replace("__", "  ")
        s = s.lines().joinToString("\n") { it.replace(Regex("[ \t]+"), " ").trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        return s.takeIf { it.isNotEmpty() }
    }
}

private fun XMLStreamReader.attr(name: String): String? = getAttributeValue(null, name)
