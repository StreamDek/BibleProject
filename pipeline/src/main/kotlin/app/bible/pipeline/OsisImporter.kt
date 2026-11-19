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
    val paragraphStart: Boolean,
    val poetry: Boolean,
    var weightAfter: Int = 0,
)

data class ImportedBible(val verses: List<ImportedVerse>, val headings: Map<Int, String>)

/**
 * Reads an OSIS file (milestone verses, as published by eBible.org) into canonical verses.
 * Only the 66 canonical books are imported. Text is copied exactly; only runs of whitespace are collapsed.
 */
object OsisImporter {

    fun parse(file: File): ImportedBible = file.inputStream().buffered().use { input ->
        val f = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        Parser(f.createXMLStreamReader(input, "UTF-8")).run()
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
                    // Trim surrounding spaces out of the span.
                    var s = italicStart; var e = sb.length
                    while (s < e && sb[s] == ' ') s++
                    while (e > s && sb[e - 1] == ' ') e--
                    if (e > s) italics += s until e
                    italicStart = -1
                }
            }
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
            sb.clear(); italics.clear()
            verseParagraph = pendingParagraph
            pendingParagraph = false
            versePoetry = lineDepth > 0
        }

        private fun endVerse() {
            val id = current!!
            // Trim, shifting italic spans to match.
            val lead = sb.indexOfFirst { it != ' ' }.coerceAtLeast(0)
            val text = sb.toString().trim()
            val spans = italics.map { (it.first - lead) until (it.last + 1 - lead) }
                .map { it.first.coerceAtLeast(0)..it.last.coerceAtMost(text.length - 1) }
                .filter { !it.isEmpty() }
            require(text.isNotEmpty()) { "Empty verse $id" }
            verses += ImportedVerse(id, text, spans, verseParagraph, versePoetry)
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
}
