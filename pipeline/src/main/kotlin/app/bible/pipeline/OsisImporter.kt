package app.bible.pipeline

import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.feed.Boundary
import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader

/** One imported verse. [text] is the translation's exact wording with whitespace normalised only. */
data class ImportedVerse(
    val id: VerseId,
    val text: String,
    val italics: List<IntRange>,
    /** Words of Jesus (OSIS `<q who="Jesus">`), as character ranges, for red-letter display. */
    val red: List<IntRange>,
    var paragraphStart: Boolean,
    val poetry: Boolean,
    var weightAfter: Int = 0,
)

data class ImportedBible(
    val verses: List<ImportedVerse>,
    /** Psalm superscriptions, keyed by chapter id (BBCCC000). Canonical text. */
    val headings: Map<Int, String>,
    /** Our own section headings, keyed by the first verse of the section. Editorial, not Scripture. */
    val sections: Map<VerseId, String> = emptyMap(),
)

/**
 * Reads an OSIS file (milestone verses, as published by eBible.org) into canonical verses.
 * Only the 66 canonical books are imported. Text is copied exactly; only runs of whitespace are collapsed.
 */
object OsisImporter {

    /**
     * @param extraParagraphs paragraph starts from editorial data, used where the KJV source has no ¶ marks.
     *   Applied only where the KJV verse before ends a sentence or clause (. ? ! or :), so a borrowed break never
     *   splits a KJV sentence.
     * @param sections first verses of our own sections; the break before them gets weight [Boundary.SECTION].
     */
    fun parse(
        file: File,
        extraParagraphs: Set<VerseId> = emptySet(),
        sections: Map<VerseId, String> = emptyMap(),
    ): ImportedBible = file.inputStream().buffered().use { input ->
        val f = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        val bible = Parser(f.createXMLStreamReader(input, "UTF-8")).run()
        applyEditorial(bible, extraParagraphs, sections)
    }

    private fun applyEditorial(bible: ImportedBible, extra: Set<VerseId>, sections: Map<VerseId, String>): ImportedBible {
        val verses = bible.verses
        val known = verses.mapTo(HashSet()) { it.id }
        (sections.keys - known).let { require(it.isEmpty()) { "Sections start at unknown verses: $it" } }
        for (i in 0 until verses.size - 1) {
            val v = verses[i]; val next = verses[i + 1]
            if (v.weightAfter >= Boundary.CHAPTER) continue
            if (next.id in sections) {
                v.weightAfter = Boundary.SECTION
                next.paragraphStart = true
            } else if (next.id in extra && v.weightAfter < Boundary.PARAGRAPH && CLAUSE_END.containsMatchIn(v.text)) {
                v.weightAfter = Boundary.PARAGRAPH
                next.paragraphStart = true
            }
        }
        return bible.copy(sections = sections)
    }

    private class Parser(private val r: XMLStreamReader) {
        val verses = ArrayList<ImportedVerse>(31_200)
        val headings = HashMap<Int, String>()

        var inCanonBook = false
        var bookNumber = 0
        var currentChapter = 0
        var current: VerseId? = null
        val sb = StringBuilder()
        val italics = ArrayList<IntRange>()
        var italicStart = -1
        val red = ArrayList<IntRange>()
        var redStart = -1
        /** sID of the open `<q who="Jesus">` milestone; quotations can run across verses. */
        var jesusQuote: String? = null
        var pendingParagraph = false
        var verseParagraph = false
        var lineDepth = 0
        var versePoetry = false
        var titleDepth = 0
        var titleKind = ""
        val titleText = StringBuilder()

        fun run(): ImportedBible {
            while (r.hasNext()) {
                when (r.next()) {
                    START_ELEMENT -> start()
                    END_ELEMENT -> end()
                    CHARACTERS -> chars(r.text)
                }
            }
            check(current == null) { "Unterminated verse $current" }
            check(jesusQuote == null) { "Unterminated words-of-Jesus quotation $jesusQuote" }
            computeWeights()
            return ImportedBible(verses, headings)
        }

        private fun attr(name: String): String? = r.getAttributeValue(null, name)

        private fun start() {
            when (r.localName) {
                "div" -> if (attr("type") == "book") {
                    val b = attr("osisID")?.let { Canon.byOsis(it) }
                    inCanonBook = b != null
                    bookNumber = b?.number ?: 0
                }
                "chapter" -> if (inCanonBook) attr("n")?.toIntOrNull()?.let { currentChapter = it }
                "p" -> if (inCanonBook) pendingParagraph = true
                "l" -> lineDepth++
                "title" -> if (inCanonBook && current == null && attr("type") == "psalm") {
                    titleDepth++; titleKind = "psalm"; titleText.clear()
                }
                "transChange" -> if (current != null && attr("type") == "added") italicStart = sb.length
                "q" -> {
                    val sid = attr("sID"); val eid = attr("eID")
                    if (sid != null && attr("who") == "Jesus" && inCanonBook) {
                        jesusQuote = sid
                        if (current != null) redStart = sb.length
                    } else if (eid != null && eid == jesusQuote) {
                        if (current != null && redStart >= 0) closeSpan(red, redStart)
                        redStart = -1
                        jesusQuote = null
                    }
                }
                "verse" -> {
                    val sid = attr("sID"); val eid = attr("eID"); val osis = attr("osisID")
                    if (sid != null && osis != null && inCanonBook) beginVerse(osis)
                    if (eid != null && current != null) endVerse()
                }
            }
        }

        private fun end() {
            when (r.localName) {
                "l" -> lineDepth--
                "title" -> if (titleDepth > 0) {
                    titleDepth--
                    headings[bookNumber * 1_000_000 + currentChapter * 1_000] = normalize(titleText.toString()).trim()
                }
                "transChange" -> if (italicStart >= 0) {
                    closeSpan(italics, italicStart)
                    italicStart = -1
                }
            }
        }

        /** Adds [start, sb.length) to [spans], with surrounding spaces trimmed out. */
        private fun closeSpan(spans: MutableList<IntRange>, start: Int) {
            var s = start; var e = sb.length
            while (s < e && sb[s] == ' ') s++
            while (e > s && sb[e - 1] == ' ') e--
            if (e > s) spans += s until e
        }

        private fun chars(text: String) {
            if (titleDepth > 0) { titleText.append(text); return }
            if (current == null) return
            val n = normalize(text)
            if (n.isEmpty()) return
            // Avoid double spaces where chunks meet.
            if (n.startsWith(' ') && (sb.isEmpty() || sb.last() == ' ')) sb.append(n.substring(1)) else sb.append(n)
        }

        private fun beginVerse(osis: String) {
            val ids = osis.split(' ')
            require(ids.size == 1) { "Combined verses are not supported yet: $osis" }
            current = VerseId.parseOsis(ids[0]) ?: error("Not a canonical KJV verse: $osis")
            sb.clear(); italics.clear(); red.clear()
            redStart = if (jesusQuote != null) 0 else -1
            verseParagraph = pendingParagraph
            pendingParagraph = false
            versePoetry = lineDepth > 0
        }

        private fun endVerse() {
            val id = current!!
            if (jesusQuote != null && redStart >= 0) closeSpan(red, redStart) // quotation continues in the next verse
            redStart = -1
            // Trim, shifting italic spans to match.
            val lead = sb.indexOfFirst { it != ' ' }.coerceAtLeast(0)
            val text = sb.toString().trim()
            fun shift(list: List<IntRange>) = list.map { (it.first - lead) until (it.last + 1 - lead) }
                .map { it.first.coerceAtLeast(0)..it.last.coerceAtMost(text.length - 1) }
                .filter { !it.isEmpty() }
            require(text.isNotEmpty()) { "Empty verse $id" }
            verses += ImportedVerse(id, text, shift(italics), shift(red), verseParagraph, versePoetry)
            current = null
        }

        private fun computeWeights() {
            for (i in verses.indices) {
                val v = verses[i]
                val next = verses.getOrNull(i + 1)
                v.weightAfter = when {
                    next == null || next.id.chapter != v.id.chapter || next.id.book != v.id.book -> Boundary.CHAPTER
                    next.paragraphStart -> Boundary.PARAGRAPH
                    SENTENCE_END.containsMatchIn(v.text) -> Boundary.SENTENCE
                    else -> Boundary.NONE
                }
            }
        }

        private fun normalize(s: String) = WHITESPACE.replace(s, " ")
    }

    private val WHITESPACE = Regex("\\s+")
    /** A verse ends a sentence when it ends with . ? or ! (optionally followed by closing quotes/brackets). */
    val SENTENCE_END = Regex("[.?!][’”'\")\\]]*$")
    /** A sentence end, or a colon (KJV often uses a colon where modern editions end a sentence). */
    val CLAUSE_END = Regex("[.?!:][’”'\")\\]]*$")
}
