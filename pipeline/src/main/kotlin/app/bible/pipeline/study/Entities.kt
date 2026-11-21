package app.bible.pipeline.study

import app.bible.core.Canon
import app.bible.core.VerseId
import java.io.File

/** A name form of a person, place or other entity (one STEP dStrong). */
data class EntityName(val stepId: String, val significance: String, val original: String, val translated: String, val refs: List<VerseId>)

/**
 * A person, place or other named thing from STEP Bible's TIPNR. Only the data fields are kept: STEP's
 * description, the summary it builds from the data, family and place links, and references. TIPNR's longer
 * descriptions (@Brief, @Short, @Article) were adapted from AI output, so they are not imported.
 */
data class EntityRecord(
    val uniqueName: String,
    val name: String,
    val kind: String,
    val type: String,
    val description: String?,
    val summary: String?,
    val tribe: String?,
    val area: String?,
    val lat: Double?,
    val lon: Double?,
    /** Relation ("parent", "sibling", "partner", "child", "founder", "resident") to another entity's unique name. */
    val links: List<Pair<String, String>>,
    val names: List<EntityName>,
)

object TipnrParser {
    private val HEADER = Regex("^\\$=+ *(PERSON\\(s\\)|PLACE\\+PERSON|PLACE|OTHER|EXCLUDED OTHER)")
    private val REF = Regex("^(?:LXX\\s*)?([1-3A-Za-z]{3})\\.(\\d+)\\.(\\d+)[a-z]?$")
    private val STEP_ID = Regex("^([HG]\\d{4}[A-Za-z]?)")
    /** A record's first line: "Abraham@Gen.11.26-1Pe=H0085". */
    private val HEAD_LINE = Regex("^[^\\s*–#][^\\t]*@[^\\t]*=[HG]\\d{4}")
    private val COORDS = Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
    private val books = OriginalTextParser.BOOKS.withIndex().associate { (i, b) -> b to i + 1 }

    fun parse(file: File, quarantine: Quarantine): List<EntityRecord> {
        val out = ArrayList<EntityRecord>()
        var kind: String? = null
        var head: List<String>? = null
        val names = ArrayList<EntityName>()
        fun flush() {
            val h = head ?: return
            val k = kind ?: return
            head = null
            val unique = h[0].substringBefore('=').trim()
            if (unique.isEmpty() || '@' !in unique) { quarantine.add("step-tipnr", "record without a name: ${h[0].take(60)}"); names.clear(); return }
            val person = k == "person"
            fun field(i: Int) = h.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() && it != ">" }
            fun list(i: Int) = h.getOrNull(i).orEmpty().split(',', '+').map { it.trim() }.filter { '@' in it }
            val links = ArrayList<Pair<String, String>>()
            if (person) {
                list(2).forEach { links += "parent" to it }
                list(3).forEach { links += "sibling" to it }
                list(4).forEach { links += "partner" to it }
                list(5).forEach { links += "child" to it }
            } else if (k == "place") {
                list(2).forEach { links += "founder" to it }
                list(3).forEach { links += "resident" to it }
            }
            val summaryField = h.firstOrNull { it.startsWith("#") }
            val coords = if (k == "place") h.getOrNull(4)?.let { COORDS.find(it) } else null
            out += EntityRecord(
                uniqueName = unique,
                name = unique.substringBefore('@').substringAfterLast('|'),
                kind = k,
                type = h.lastOrNull { it.isNotBlank() && !it.startsWith("#") && it.trim() != ">" }?.trim().orEmpty(),
                description = if (k == "place") null else field(1),
                summary = summaryField?.let { plain(it.removePrefix("#")) },
                tribe = if (person) field(6) else null,
                area = if (k == "place") field(6) else null,
                lat = coords?.groupValues?.get(1)?.toDouble(),
                lon = coords?.groupValues?.get(2)?.toDouble(),
                links = links,
                names = names.toList(),
            )
            names.clear()
        }
        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trimEnd('\r')
                HEADER.find(line)?.let { m ->
                    flush()
                    kind = when (m.groupValues[1]) {
                        "PERSON(s)" -> "person"
                        "PLACE", "PLACE+PERSON" -> "place"
                        "OTHER" -> "other"
                        else -> null
                    }
                    return@let
                } ?: run {
                    if (kind == null) return@run
                    val cols = line.split('\t').map { it.trim() }.dropLastWhile { it.isEmpty() }
                    when {
                        line.startsWith("@") || line.isBlank() -> {}
                        line.startsWith("– Total") || line.startsWith("- Total") -> {}
                        line.startsWith("–") && head != null -> {
                            // "– Named  UniqueName  dStrong«eStrong=Hebrew  Translated  Refs"
                            val id = STEP_ID.find(cols.getOrNull(2).orEmpty())?.groupValues?.get(1) ?: return@run
                            if (id.substring(1, 5) == "0000") return@run // an unnamed person: no name form to link
                            val refs = cols.getOrNull(4).orEmpty().split(';').mapNotNull { r ->
                                val m = REF.matchEntire(r.trim()) ?: return@mapNotNull null
                                val b = books[m.groupValues[1]] ?: return@mapNotNull null
                                VerseId.of(b, m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf { Canon.isValid(it) }
                            }.distinct()
                            names += EntityName(
                                stepId = id,
                                significance = cols[0].removePrefix("–").trim(),
                                original = cols.getOrNull(2).orEmpty().substringAfter('=', "").trim(),
                                translated = cols.getOrNull(3).orEmpty(),
                                refs = refs,
                            )
                        }
                        head == null && HEAD_LINE.containsMatchIn(cols.firstOrNull() ?: "") -> head = cols
                    }
                }
            }
        }
        flush()
        return out
    }

    /** TIPNR's summary markup as plain text: <br> becomes a line break; <ref> and <strong> keep their text. */
    fun plain(s: String): String = s
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace(") \n", "\n").replace(")\n", "\n")
        .lines().joinToString("\n") { it.trim().replace(Regex("\\s+"), " ") }
        .replace(Regex("\n{2,}"), "\n").trim()
}

/** A cross-reference from OpenBible.info: from one verse to a verse or a range, with its vote count. */
data class CrossRef(val from: VerseId, val toStart: VerseId, val toEnd: VerseId, val votes: Int)

object CrossRefParser {
    private val ref = Regex("^([1-3]?[A-Za-z]+)\\.(\\d+)\\.(\\d+)$")

    private fun verse(s: String): VerseId? {
        val m = ref.matchEntire(s.trim()) ?: return null
        val book = Canon.byOsis(m.groupValues[1]) ?: return null
        return VerseId.of(book.number, m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf { Canon.isValid(it) }
    }

    /** Keeps references with at least [minVotes] votes; references to verses outside KJV numbering are reported. */
    fun parse(file: File, minVotes: Int, quarantine: Quarantine): List<CrossRef> {
        val out = ArrayList<CrossRef>()
        var bad = 0
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("From Verse") || line.isBlank()) continue
                val c = line.split('\t')
                val votes = c.getOrNull(2)?.trim()?.toIntOrNull() ?: continue
                if (votes < minVotes) continue
                val from = verse(c[0])
                val toStart = verse(c[1].substringBefore('-'))
                val toEnd = if ('-' in c[1]) verse(c[1].substringAfter('-')) else toStart
                if (from == null || toStart == null || toEnd == null || toEnd < toStart) { bad++; continue }
                out += CrossRef(from, toStart, toEnd, votes)
            }
        }
        if (bad > 0) quarantine.add("openbible-xrefs", "$bad references name a verse outside KJV numbering; left out")
        return out.distinctBy { Triple(it.from, it.toStart, it.toEnd) }
    }
}
