package app.bible.pipeline.study

import app.bible.core.VerseId
import app.bible.data.study.Fold
import app.bible.data.study.StrongsNumber
import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT

/** A run of CrossWire KJV text: either a tagged <w> element or plain text between them. */
data class CwSegment(val text: String, val nums: List<Int>, val morph: List<String>, val src: String)

/** A tag mapped onto VerseUp's own KJV text. */
data class MappedTag(val start: Int, val end: Int, val nums: List<Int>, val morph: List<String>, val src: String)

data class TagStats(
    var verses: Int = 0,
    var segments: Int = 0,
    var tagged: Int = 0,
    var mapped: Int = 0,
    var unmappedTags: Int = 0,
    var ourWords: Int = 0,
    var ourWordsMatched: Int = 0,
    var emptySourceWords: Int = 0,
    var titleTagsSkipped: Int = 0,
    val weakVerses: MutableList<String> = ArrayList(),
)

/**
 * Reads the CrossWire KJV (OSIS with Strong's numbers on <w lemma="strong:H07225">) and maps each tagged phrase onto
 * the KJV text VerseUp shows (eBible.org). The two editions are the same 1769 text but differ in a few spellings and
 * marks, so words are matched by a longest-common-subsequence alignment per verse; a tag is kept only where its own
 * words are found in VerseUp's verse. Nothing is guessed: an unmatched tag is counted in the report and dropped.
 */
object KjvTagger {
    private val WORD = Regex("\\p{L}+")

    fun parse(file: File, stats: TagStats): Map<VerseId, List<CwSegment>> {
        val r = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }.createXMLStreamReader(file.inputStream().buffered(), "UTF-8")
        val out = LinkedHashMap<VerseId, MutableList<CwSegment>>()
        var verse: VerseId? = null
        var segments: MutableList<CwSegment>? = null
        var skip = 0 // inside <note> or <title>
        var inTitle = false
        // The open <w>: its attributes and text.
        var wNums: List<Int>? = null
        var wMorph: List<String> = emptyList()
        var wSrc = ""
        val wText = StringBuilder()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) { segments?.add(CwSegment(plain.toString(), emptyList(), emptyList(), "")); plain.setLength(0) }
        }

        while (r.hasNext()) {
            when (r.next()) {
                START_ELEMENT -> {
                    val name = r.localName
                    if (skip > 0) {
                        skip++
                        if (inTitle && name == "w" && r.getAttributeValue(null, "lemma")?.contains("strong:") == true) stats.titleTagsSkipped++
                        continue
                    }
                    when (name) {
                        "verse" -> {
                            val sid = r.getAttributeValue(null, "sID")
                            val eid = r.getAttributeValue(null, "eID")
                            if (sid != null) {
                                verse = VerseId.parseOsis(r.getAttributeValue(null, "osisID") ?: sid)
                                segments = verse?.let { out.getOrPut(it) { ArrayList() } }
                            } else if (eid != null) {
                                flushPlain(); verse = null; segments = null
                            }
                        }
                        "note" -> skip = 1
                        "title" -> { skip = 1; inTitle = true }
                        "w" -> if (segments != null) {
                            flushPlain()
                            val lemma = r.getAttributeValue(null, "lemma") ?: ""
                            wNums = lemma.split(' ').filter { it.startsWith("strong:") }
                                .mapNotNull { StrongsNumber.parse(it.removePrefix("strong:")) }
                            wMorph = (r.getAttributeValue(null, "morph") ?: "").split(' ').filter { it.isNotEmpty() }
                                .map { it.substringAfter(':') }
                            wSrc = r.getAttributeValue(null, "src") ?: ""
                            wText.setLength(0)
                        }
                    }
                }
                CHARACTERS -> if (skip == 0 && segments != null) {
                    if (wNums != null) wText.append(r.text) else plain.append(r.text)
                }
                END_ELEMENT -> {
                    if (skip > 0) { skip--; if (skip == 0) inTitle = false; continue }
                    if (r.localName == "w" && wNums != null) {
                        val text = wText.toString()
                        if (text.isBlank()) stats.emptySourceWords++
                        else segments?.add(CwSegment(text, wNums!!, wMorph, wSrc))
                        wNums = null
                    }
                }
            }
        }
        return out
    }

    private fun norm(w: String): String = Fold.fold(w).replace("æ", "e").replace("œ", "e")

    /** Maps the segments of one verse onto [text] (VerseUp's verse text). */
    fun map(verse: VerseId, text: String, segments: List<CwSegment>, stats: TagStats): List<MappedTag> {
        stats.verses++
        // Our words with their character ranges.
        val ours = WORD.findAll(text).map { it.range to norm(it.value) }.toList()
        // CrossWire words, each remembering its segment.
        val theirs = ArrayList<Pair<String, Int>>()
        segments.forEachIndexed { i, s -> WORD.findAll(s.text).forEach { theirs += norm(it.value) to i } }
        stats.segments += segments.size
        val tagged = segments.indices.filter { segments[it].nums.isNotEmpty() }
        stats.tagged += tagged.size
        stats.ourWords += ours.size

        // Longest common subsequence of the two word lists.
        val n = ours.size; val m = theirs.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (ours[i].second == theirs[j].first) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val segOf = IntArray(n) { -1 }
        var i = 0; var j = 0
        while (i < n && j < m) {
            when {
                ours[i].second == theirs[j].first -> { segOf[i] = theirs[j].second; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val matched = segOf.count { it >= 0 }
        stats.ourWordsMatched += matched
        if (n > 0 && matched < n * 0.9) stats.weakVerses += "${verse.osis()} matched $matched of $n words"

        val out = ArrayList<MappedTag>()
        for (s in tagged) {
            val words = (0 until n).filter { segOf[it] == s }
            if (words.isEmpty()) { stats.unmappedTags++; continue }
            val seg = segments[s]
            out += MappedTag(ours[words.first()].first.first, ours[words.last()].first.last + 1, seg.nums, seg.morph, seg.src)
            stats.mapped++
        }
        return out.sortedBy { it.start }
    }

    /** study.db's compact form: "start,end,nums,morph,src" joined by ';'. */
    fun encode(tags: List<MappedTag>): String = tags.joinToString(";") { t ->
        "${t.start},${t.end},${t.nums.joinToString(" ")},${t.morph.joinToString(" ")},${t.src}"
    }
}
