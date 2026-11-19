package app.bible.pipeline

import app.bible.core.Canon
import app.bible.core.VerseId
import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.START_ELEMENT

/**
 * Extracts paragraph starts from a USFX Bible (for example the public-domain World English Bible).
 *
 * The KJV source only marks paragraphs in part of the Bible, so we borrow paragraph *positions* (never text)
 * from a public-domain modern edition with the same versification. A verse counts as a paragraph start when a
 * `<p>` or a stanza break `<b>` opens immediately before it, with no text in between.
 */
object ParagraphExtractor {

    /** Paratext book codes in canonical order (index + 1 = book number). */
    val PARATEXT_CODES = listOf(
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH", "EZR", "NEH",
        "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON",
        "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL", "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL",
        "EPH", "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN",
        "JUD", "REV",
    )
    private val bookByCode = PARATEXT_CODES.withIndex().associate { (i, code) -> code to i + 1 }

    fun extract(usfx: File): List<VerseId> = usfx.inputStream().buffered().use { input ->
        val f = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        val r = f.createXMLStreamReader(input, "UTF-8")
        val out = ArrayList<VerseId>(9_000)
        var book = 0
        var chapter = 0
        var pending = false
        var noteDepth = 0
        while (r.hasNext()) {
            when (r.next()) {
                START_ELEMENT -> when (r.localName) {
                    "book" -> { book = bookByCode[r.getAttributeValue(null, "id")] ?: 0; chapter = 0; pending = false }
                    "c" -> { chapter = r.getAttributeValue(null, "id")?.toIntOrNull() ?: 0; pending = false }
                    "p", "b" -> if (book != 0) pending = true
                    "f", "x" -> noteDepth++
                    "v" -> {
                        val verse = r.getAttributeValue(null, "id")?.toIntOrNull()
                        if (pending && book != 0 && chapter > 0 && verse != null && verse > 1) {
                            val id = VerseId.of(book, chapter, verse)
                            if (Canon.isValid(id)) out += id
                        }
                        pending = false
                    }
                }
                CHARACTERS -> if (noteDepth == 0 && r.text.isNotBlank()) pending = false
                javax.xml.stream.XMLStreamConstants.END_ELEMENT -> if (r.localName == "f" || r.localName == "x") noteDepth--
            }
        }
        out.distinct().sorted()
    }

    /** Writes one OSIS verse reference per line, with a header comment. */
    fun write(ids: List<VerseId>, out: File, source: String) {
        out.parentFile.mkdirs()
        out.bufferedWriter().use { w ->
            w.append("# Paragraph starts (verse positions only, no text). Derived from: $source\n")
            w.append("# One OSIS reference per line. Editors may add or remove lines; '#' starts a comment.\n")
            ids.forEach { w.append(it.osis()).append('\n') }
        }
    }
}

/** Editorial boundary data that the importer merges into the KJV verse weights. */
object EditorialBreaks {

    /** Reads a list of OSIS references (one per line, '#' comments). */
    fun readParagraphs(file: File): Set<VerseId> = file.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { VerseId.parseOsis(it) ?: error("${file.name}: not a canonical verse: $it") }
        .toSet()

    /**
     * Reads our own section headings: "OSIS-ref<TAB>Heading" per line, '#' comments.
     * The verse is the first verse of the section. Headings are written by our editors, not copied from any edition.
     */
    fun readSections(file: File): Map<VerseId, String> = file.readLines()
        .map { it.substringBefore('#').trimEnd() }
        .filter { it.isNotBlank() }
        .associate { line ->
            val parts = line.split('\t')
            require(parts.size == 2 && parts[1].isNotBlank()) { "${file.name}: expected 'ref<TAB>heading': $line" }
            val id = VerseId.parseOsis(parts[0].trim()) ?: error("${file.name}: not a canonical verse: ${parts[0]}")
            id to parts[1].trim()
        }
}
