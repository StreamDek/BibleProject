package app.bible.pipeline.study

import app.bible.core.AttributionRegistry
import app.bible.core.VerseId
import app.bible.data.study.Fold
import app.bible.data.study.StrongsNumber
import app.bible.data.study.StudyDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager

/**
 * Builds study.db, the offline word-study pack: both lexicons (kept apart), the KJV's word tags mapped onto
 * VerseUp's text, occurrence and rendering indexes and a full-text index. Also writes an import report that lists
 * counts, every quarantined record and verses whose words did not fully match.
 */
object StudyDbWriter {
    /** Bump when the study.db schema changes. */
    const val SCHEMA_VERSION = 2

    /** English words left out of the English → Strong's index (they translate almost everything). */
    private val FUNCTION_WORDS = setOf(
        "a", "an", "and", "the", "of", "to", "in", "that", "for", "with", "as", "at", "by", "from", "on", "unto",
        "upon", "into", "or", "be", "is", "was", "were", "are", "it", "he", "she", "they", "them", "him", "her",
        "his", "their", "i", "me", "my", "we", "us", "our", "ye", "you", "your", "thou", "thee", "thy", "s",
    )
    private val WORD = Regex("\\p{L}+")

    /**
     * The Greek article (G3588) and the Hebrew object marker (H853) often share a tagged phrase with the word they
     * go with ("the love" = G3588 + G26). They keep the occurrence, but the phrase's English is credited to the
     * other word only.
     */
    private val MARKERS = setOf(StrongsNumber.of('G', 3588), 853)

    class Result(val report: String)

    fun build(cacheDir: File, bibleDb: File, registryFile: File, out: File, reportFile: File): Result {
        val registry = AttributionRegistry.decode(registryFile.readText())
        for (s in StudySources.all) check(registry.byId(s.registryId) != null) { "attributions.json has no entry for ${s.registryId}" }
        val problems = registry.problems()
        check(problems.isEmpty()) { "attributions.json: " + problems.joinToString("; ") }

        val files = StudySources.all.associate { it.key to StudySources.fetch(it, cacheDir) }
        val quarantine = Quarantine()
        val strongs = StrongsHebrewParser.parse(files.getValue("strongs-hebrew"), "strongs-hebrew", quarantine) +
            StrongsGreekParser.parse(files.getValue("strongs-greek"), "strongs-greek", quarantine)
        val step = StepLexiconParser.parse(files.getValue("step-tbesh"), "step-tbesh", keepMeaning = false, quarantine) +
            StepLexiconParser.parse(files.getValue("step-tbesg"), "step-tbesg", keepMeaning = true, quarantine)
        val known = strongs.mapTo(HashSet()) { it.num }

        // KJV tags mapped onto our own verse text.
        val stats = TagStats()
        val segments = KjvTagger.parse(files.getValue("crosswire-kjv"), stats)
        val verses = readVerses(bibleDb)
        val tags = LinkedHashMap<VerseId, List<MappedTag>>()
        var missingVerses = 0
        for ((id, text) in verses) {
            val seg = segments[id]
            if (seg == null) { missingVerses++; quarantine.add("crosswire-kjv", "no verse ${id.osis()} in the tagged KJV"); continue }
            val mapped = KjvTagger.map(id, text, seg, stats)
            if (mapped.isNotEmpty()) tags[id] = mapped
        }
        val unknownNums = tags.values.flatten().flatMap { it.nums }.filter { it !in known }.groupingBy { it }.eachCount()
        for ((n, c) in unknownNums.toSortedMap()) quarantine.add("crosswire-kjv", "tag ${StrongsNumber.id(n)} ($c×) has no Strong's entry; kept on the word, no lexicon link")

        // Indexes derived from the tags.
        val occurrence = sortedSetOf<Long>() // num * 1e9 + verse
        val words = HashMap<Int, Int>()
        val rendering = HashMap<Int, HashMap<String, Int>>()
        val english = HashMap<String, HashMap<Int, Int>>()
        for ((id, list) in tags) {
            val text = verses.getValue(id)
            for (t in list) {
                val phrase = WORD.findAll(text.substring(t.start, t.end)).map { Fold.fold(it.value) }.toList()
                val credited = t.nums.distinct().let { all -> if (all.size > 1 && all.any { it !in MARKERS }) all.filter { it !in MARKERS } else all }
                for (n in t.nums.distinct()) {
                    occurrence += n * 1_000_000_000L + id.value
                    words.merge(n, 1, Int::plus)
                    if (n !in credited) continue
                    if (phrase.isNotEmpty()) rendering.getOrPut(n) { HashMap() }.merge(phrase.joinToString(" "), 1, Int::plus)
                    for (w in phrase.toSet()) if (w !in FUNCTION_WORDS) english.getOrPut(w) { HashMap() }.merge(n, 1, Int::plus)
                }
            }
        }
        val verseCounts = occurrence.groupingBy { (it / 1_000_000_000L).toInt() }.eachCount()

        // The Hebrew and Greek text, word by word, in the KJV's verse numbering.
        val hebStats = OrigStats(); val grcStats = OrigStats()
        val hebrew = OriginalTextParser.parseHebrew(StudySources.tahot.map { files.getValue(it.key) }, hebStats)
        val greek = OriginalTextParser.parseGreek(StudySources.tagnt.map { files.getValue(it.key) }, grcStats)
        val original = sortedMapOf<VerseId, Pair<String, List<OrigWord>>>()
        hebrew.forEach { (id, w) -> original[id] = "heb" to w }
        greek.forEach { (id, w) -> original[id] = "grc" to w }
        for (r in hebStats.badRefs) quarantine.add("step-tahot", "reference $r is not a KJV verse; its words are left out")
        for (r in grcStats.badRefs) quarantine.add("step-tagnt", "reference $r is not a KJV verse; its words are left out")
        val noOriginal = verses.keys.filter { it !in original }
        val stepIds = step.mapTo(HashSet()) { it.id }
        val unknownIds = sortedMapOf<String, Int>()
        val senseVerses = HashMap<String, Int>()
        for ((_, pair) in original) {
            val seen = HashSet<String>()
            for (w in pair.second) for (id in OriginalTextParser.ids(w.strongs)) {
                if (id !in stepIds) unknownIds.merge(id, 1, Int::plus) else seen += id
            }
            seen.forEach { senseVerses.merge(it, 1, Int::plus) }
        }
        for ((id, n) in unknownIds) quarantine.add("step-text", "tag $id ($n×) is not in STEP's lexicons; kept on the word, no lexicon link")

        // Grammar explanations for the codes the text uses.
        val morphs = (MorphologyParser.parse(files.getValue(StudySources.tehmc.key)) + MorphologyParser.parse(files.getValue(StudySources.tegmc.key))).associateBy { it.code }
        val usedCodes = sortedSetOf<String>()
        for ((_, pair) in original) for (w in pair.second) usedCodes += MorphologyParser.codes(w.grammar, pair.first == "heb")
        val missingCodes = usedCodes.filter { it !in morphs }
        for (code in missingCodes) quarantine.add("step-morphology", "grammar code $code has no explanation")

        out.parentFile.mkdirs()
        listOf("", "-journal", "-wal", "-shm").forEach { File(out.path + it).delete() }
        JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}").use { StudyDatabase.Schema.create(it) }
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { c ->
            c.autoCommit = false
            insert(c, "INSERT INTO meta(key, value) VALUES (?, ?)", listOf(
                listOf("schema_version", "$SCHEMA_VERSION"),
                listOf("translation", "kjv"),
                listOf("tagged_verses", "${tags.size}"),
                listOf("tags", "${stats.mapped}"),
            ))
            insert(c, "INSERT INTO source(id, name, licence, url, version) VALUES (?, ?, ?, ?, ?)",
                StudySources.all.distinctBy { it.registryId }.map { s -> registry.byId(s.registryId)!!.let { listOf(s.registryId, it.name, it.licence, it.url, s.version) } })
            insert(c, "INSERT INTO strongs(num, lang, lemma, xlit, pron, pos, derivation, definition, kjv_def, source_id, verses, words) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                strongs.sortedBy { it.num }.map { s ->
                    listOf(s.num, s.lang, s.lemma, s.xlit, s.pron, s.pos, s.derivation, s.definition, s.kjvDef, s.sourceId, verseCounts[s.num] ?: 0, words[s.num] ?: 0)
                })
            val brokenRefs = ArrayList<String>()
            insert(c, "INSERT INTO strongs_ref(num, ref) VALUES (?, ?)", strongs.sortedBy { it.num }.flatMap { s ->
                s.refs.sorted().mapNotNull { r ->
                    if (r in known) listOf(s.num, r) else { brokenRefs += "${StrongsNumber.id(s.num)} → ${StrongsNumber.id(r)}"; null }
                }
            })
            brokenRefs.forEach { quarantine.add("strongs", "derivation names $it, which has no entry; link left out") }
            insert(c, "INSERT INTO step_entry(id, num, estrong, ustrong, label, lang, lemma, xlit, morph, gloss, meaning, source_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                step.sortedBy { it.id }.map { s -> listOf(s.id, s.num, s.eStrong, s.uStrong, s.label, s.lang, s.lemma, s.xlit, s.morph, s.gloss, s.meaning, s.sourceId) })
            insert(c, "INSERT INTO kjv_tags(verse_id, tags) VALUES (?, ?)", tags.map { (id, list) -> listOf(id.value, KjvTagger.encode(list)) })
            insert(c, "INSERT INTO occurrence(num, verse_id) VALUES (?, ?)", occurrence.map { listOf((it / 1_000_000_000L).toInt(), (it % 1_000_000_000L).toInt()) })
            insert(c, "INSERT INTO rendering(num, text, count) VALUES (?, ?, ?)", rendering.toSortedMap().flatMap { (n, m) ->
                m.toSortedMap().map { (t, k) -> listOf(n, t, k) }
            })
            insert(c, "INSERT INTO english(word, num, count) VALUES (?, ?, ?)", english.toSortedMap().flatMap { (w, m) ->
                m.toSortedMap().map { (n, k) -> listOf(w, n, k) }
            })
            insert(c, "INSERT INTO orig_verse(verse_id, lang, words) VALUES (?, ?, ?)", original.map { (id, p) -> listOf(id.value, p.first, OriginalTextParser.encode(p.second)) })
            insert(c, "INSERT INTO morph(code, parsing, summary, explanation, example) VALUES (?, ?, ?, ?, ?)",
                usedCodes.mapNotNull { morphs[it] }.map { m -> listOf(m.code, m.parsing, m.summary, m.explanation, m.example) })
            insert(c, "INSERT INTO sense_count(id, verses) VALUES (?, ?)", senseVerses.toSortedMap().map { (id, n) -> listOf(id, n) })

            // Full-text index over both lexicons, folded so accents, vowel points and transliteration marks don't matter.
            val docs = ArrayList<List<Any?>>()
            val fts = ArrayList<List<Any?>>()
            var docId = 1
            for (s in strongs.sortedBy { it.num }) {
                docs += listOf(docId, "strongs", "${s.num}")
                fts += listOf(docId, Fold.fold(StrongsNumber.id(s.num)), Fold.fold(s.lemma), Fold.fold(listOfNotNull(s.xlit, s.pron).joinToString(" ")),
                    Fold.fold(s.kjvDef ?: ""), Fold.fold(listOfNotNull(s.definition, s.derivation).joinToString(" ")))
                docId++
            }
            for (s in step.sortedBy { it.id }) {
                docs += listOf(docId, "step", s.id)
                fts += listOf(docId, Fold.fold(listOfNotNull(s.id, s.eStrong, s.uStrong).joinToString(" ")), Fold.fold(s.lemma),
                    Fold.fold(s.xlit ?: ""), Fold.fold(s.gloss ?: ""), "")
                docId++
            }
            insert(c, "INSERT INTO search_doc(id, kind, ref) VALUES (?, ?, ?)", docs)
            c.createStatement().use { it.execute("CREATE VIRTUAL TABLE lexicon_fts USING fts4(content=\"\", ids, lemma, xlit, gloss, defs, tokenize=simple)") }
            insert(c, "INSERT INTO lexicon_fts(docid, ids, lemma, xlit, gloss, defs) VALUES (?, ?, ?, ?, ?, ?)", fts)
            c.createStatement().use {
                it.execute("INSERT INTO lexicon_fts(lexicon_fts) VALUES('optimize')")
                // user_version must equal the SQLDelight schema version, or platform drivers try to migrate the file.
                it.execute("PRAGMA user_version = ${StudyDatabase.Schema.version}")
            }
            c.commit()
        }
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { c -> c.createStatement().use { it.execute("VACUUM") } }

        val report = buildString {
            appendLine("# study.db import report")
            appendLine("Generated by ./gradlew :pipeline:buildStudy. Do not edit; CI checks it is current.")
            appendLine()
            appendLine("## Sources")
            for (s in StudySources.all) appendLine("- ${s.key}: ${s.version}, sha256 ${s.sha256}")
            appendLine()
            appendLine("## Counts")
            appendLine("- Strong's entries: ${strongs.count { !StrongsNumber.isGreek(it.num) }} Hebrew/Aramaic, ${strongs.count { StrongsNumber.isGreek(it.num) }} Greek")
            appendLine("- Strong's derivation links: ${strongs.sumOf { it.refs.size } - brokenRefCount(quarantine)}")
            appendLine("- STEP senses: ${step.count { it.sourceId == "step-tbesh" }} Hebrew/Aramaic (glosses only), ${step.count { it.sourceId == "step-tbesg" }} Greek (with definitions)")
            appendLine("- KJV verses: ${verses.size}; with tags: ${tags.size}; not in the tagged KJV: $missingVerses")
            appendLine("- Tagged phrases in the source: ${stats.tagged}; mapped onto VerseUp's text: ${stats.mapped}; unmatched (left out): ${stats.unmappedTags}")
            appendLine("- Untranslated source words (empty <w>, Greek only): ${stats.emptySourceWords}; Psalm title tags not used yet: ${stats.titleTagsSkipped}")
            appendLine("- VerseUp words matched to the tagged text: ${stats.ourWordsMatched} of ${stats.ourWords} (${"%.2f".format(100.0 * stats.ourWordsMatched / stats.ourWords)}%)")
            appendLine("- Occurrence rows: ${occurrence.size}; renderings: ${rendering.values.sumOf { it.size }}; English index words: ${english.size}")
            appendLine("- Hebrew words (TAHOT): ${hebStats.kept} of ${hebStats.rows} rows kept, in ${hebrew.size} verses; Psalm title words not used yet: ${hebStats.titleWords}")
            appendLine("  - Left out (not the text the KJV translates): " + hebStats.skippedTypes.entries.joinToString(", ") { "${it.key} ${it.value}" })
            appendLine("- Greek words (TAGNT, the KJV's Textus Receptus): ${grcStats.kept} of ${grcStats.rows} rows kept, in ${greek.size} verses")
            appendLine("  - Where the Traditional text has a different word, the Nestle-Aland word is shown with the Traditional reading noted: ${grcStats.flaggedTraditional}")
            appendLine("  - Left out (not in the Textus Receptus): " + grcStats.skippedTypes.entries.joinToString(", ") { "${it.key} ${it.value}" })
            appendLine("- KJV verses without original-language words: ${noOriginal.size}" + if (noOriginal.isNotEmpty()) " (" + noOriginal.take(40).joinToString(", ") { it.osis() } + if (noOriginal.size > 40) ", ...)" else ")" else "")
            appendLine("- Grammar codes used: ${usedCodes.size}; explained: ${usedCodes.size - missingCodes.size}")
            appendLine()
            appendLine("## Quarantined records (${quarantine.items.size})")
            quarantine.items.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Verses with under 90% of words matched (${stats.weakVerses.size})")
            stats.weakVerses.forEach { appendLine("- $it") }
        }
        reportFile.parentFile.mkdirs()
        reportFile.writeText(report)
        return Result(report.lines().take(20).joinToString("\n") + "\nstudy.db ${out.length() / 1024} KB")
    }

    private fun brokenRefCount(q: Quarantine) = q.items.count { it.startsWith("strongs: derivation names") }

    private fun readVerses(bibleDb: File): Map<VerseId, String> = DriverManager.getConnection("jdbc:sqlite:${bibleDb.absolutePath}").use { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT id, text FROM verse ORDER BY id").use { rs ->
                val out = LinkedHashMap<VerseId, String>()
                while (rs.next()) out[VerseId(rs.getInt(1))] = rs.getString(2)
                out
            }
        }
    }

    private fun insert(c: Connection, sql: String, rows: List<List<Any?>>) {
        c.prepareStatement(sql).use { ps ->
            for (row in rows) {
                row.forEachIndexed { i, v ->
                    when (v) {
                        null -> ps.setNull(i + 1, java.sql.Types.NULL)
                        is Int -> ps.setInt(i + 1, v)
                        is Long -> ps.setLong(i + 1, v)
                        else -> ps.setString(i + 1, v.toString())
                    }
                }
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    /** Same scheme as bible-kjv.stamp: pack schema version + start of the file's SHA-256. */
    fun writeStamp(db: File, stamp: File) {
        val digest = MessageDigest.getInstance("SHA-256").digest(db.readBytes()).joinToString("") { "%02x".format(it) }
        stamp.writeText("$SCHEMA_VERSION-${digest.take(24)}\n")
    }
}
