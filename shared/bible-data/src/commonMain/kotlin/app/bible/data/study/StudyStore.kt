package app.bible.data.study

import app.bible.core.Passage
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.bible.BibleSearch
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Strong's numbers as integers: Hebrew H430 = 430, Greek G26 = 100026. Numbers are never compared without their
 * language prefix, because H26 and G26 are unrelated words.
 */
object StrongsNumber {
    const val GREEK = 100_000
    private val ID = Regex("^\\s*([HhGg])\\s*0*(\\d{1,5})\\s*([A-Za-z]?)\\s*$")

    fun of(lang: Char, n: Int): Int = if (lang == 'G' || lang == 'g') GREEK + n else n

    /** "H430", "h0430", "G26", "H1254a" (the sense letter is ignored) → the integer, or null. */
    fun parse(id: String): Int? {
        val m = ID.matchEntire(id) ?: return null
        val n = m.groupValues[2].toInt()
        if (n == 0) return null
        return of(m.groupValues[1][0], n)
    }

    /** The sense letter of a STEP id such as "H1254A", or null. */
    fun senseLetter(id: String): Char? = ID.matchEntire(id)?.groupValues?.get(3)?.firstOrNull()?.uppercaseChar()

    fun isGreek(num: Int): Boolean = num >= GREEK

    fun id(num: Int): String = if (isGreek(num)) "G${num - GREEK}" else "H$num"
}

enum class Language(val code: String, val label: String) {
    Hebrew("heb", "Hebrew"), Aramaic("arc", "Aramaic"), Greek("grc", "Greek");

    val rightToLeft: Boolean get() = this != Greek

    companion object {
        fun of(code: String): Language = entries.firstOrNull { it.code == code } ?: Hebrew
    }
}

/** An entry of Strong's Hebrew or Greek dictionary, exactly as the edition gives it. */
data class StrongsEntry(
    val num: Int,
    val language: Language,
    val lemma: String,
    val xlit: String?,
    val pron: String?,
    val partOfSpeech: String?,
    val derivation: String?,
    val definition: String?,
    val kjvDefinition: String?,
    val sourceId: String,
    /** KJV verses tagged with this number. */
    val verses: Int,
    /** KJV words or phrases tagged with this number. */
    val words: Int,
) {
    val id: String get() = StrongsNumber.id(num)
}

/** One sense in STEP Bible's brief lexicon (a disambiguated Strong's number such as H1254A). */
data class StepEntry(
    val id: String,
    val num: Int,
    val eStrong: String,
    val uStrong: String?,
    val label: String?,
    val language: Language,
    val lemma: String,
    val xlit: String?,
    val morph: String?,
    val gloss: String?,
    /** Longer definition; Greek only (see docs/concordance.md). */
    val meaning: String?,
    val sourceId: String,
)

/**
 * One tagged English word or phrase in a KJV verse: [start] until [end] are character offsets into the verse
 * text, [nums] the Strong's numbers the KJV's tagging gives it (one phrase can translate several words).
 */
data class WordTag(val start: Int, val end: Int, val nums: List<Int>, val morph: List<String>, val src: String) {
    companion object {
        fun parseAll(tags: String): List<WordTag> = tags.split(';').mapNotNull { t ->
            val f = t.split(',')
            if (f.size < 3) return@mapNotNull null
            WordTag(
                start = f[0].toInt(),
                end = f[1].toInt(),
                nums = f[2].split(' ').mapNotNull { it.toIntOrNull() },
                morph = f.getOrNull(3)?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList(),
                src = f.getOrNull(4) ?: "",
            )
        }
    }
}

data class KjvRendering(val text: String, val count: Int)
data class BookCount(val book: Int, val verses: Int)
data class SourceInfo(val id: String, val name: String, val licence: String, val url: String, val version: String)

/** An English word and a Strong's number it translates in the KJV, with how often. */
data class EnglishHit(val word: String, val entry: StrongsEntry, val count: Int)

data class StudySearchResult(
    val query: String = "",
    /** Strong's entries matching an identifier, lemma, transliteration, gloss or definition. */
    val strongs: List<StrongsEntry> = emptyList(),
    /** STEP senses matching the query (their own ids, lemmas, transliterations and glosses). */
    val step: List<StepEntry> = emptyList(),
    /** Words the KJV translates with the English word typed, most frequent first. */
    val english: List<EnglishHit> = emptyList(),
    /** Set when the query is a Bible reference. */
    val reference: Passage? = null,
    /** People, places and other names (STEP Bible TIPNR) whose name starts with the query. */
    val entities: List<BibleEntity> = emptyList(),
    /** Close English spellings when nothing matched. */
    val suggestions: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = strongs.isEmpty() && step.isEmpty() && english.isEmpty() && entities.isEmpty() && reference == null
}

/**
 * Read-only access to study.db: Strong's, STEP Bible and the KJV's word tags. Every call runs off the main thread.
 */
class StudyStore(private val driver: SqlDriver, private val io: CoroutineContext = Dispatchers.IO) {
    private val db = StudyDatabase(driver)
    private val q get() = db.studyQueries

    suspend fun meta(key: String): String? = withContext(io) { q.metaValue(key).executeAsOneOrNull() }

    suspend fun sources(): List<SourceInfo> = withContext(io) {
        q.sources().executeAsList().map { SourceInfo(it.id, it.name, it.licence, it.url, it.version) }
    }

    suspend fun strongs(num: Int): StrongsEntry? = withContext(io) { q.strongsByNum(num.toLong()).executeAsOneOrNull()?.toEntry() }

    suspend fun strongs(nums: Collection<Int>): Map<Int, StrongsEntry> = withContext(io) {
        if (nums.isEmpty()) emptyMap()
        else q.strongsByNums(nums.map { it.toLong() }).executeAsList().map { it.toEntry() }.associateBy { it.num }
    }

    /** Entries Strong's names in this entry's derivation. */
    suspend fun derivedFrom(num: Int): List<StrongsEntry> = withContext(io) { q.refsOf(num.toLong()).executeAsList().map { it.toEntry() } }

    /** Entries whose derivation names this one. */
    suspend fun derivatives(num: Int): List<StrongsEntry> = withContext(io) { q.referencedBy(num.toLong()).executeAsList().map { it.toEntry() } }

    suspend fun stepSenses(num: Int): List<StepEntry> = withContext(io) { q.stepByNum(num.toLong()).executeAsList().map { it.toStep() } }

    suspend fun step(id: String): StepEntry? = withContext(io) { q.stepById(id).executeAsOneOrNull()?.toStep() }

    /** The KJV word tags of each verse in [range]. */
    suspend fun tags(range: VerseRange): Map<VerseId, List<WordTag>> = withContext(io) {
        q.tagsFor(range.start.value.toLong(), range.end.value.toLong()).executeAsList()
            .associate { VerseId(it.verse_id.toInt()) to WordTag.parseAll(it.tags) }
    }

    /** The Hebrew or Greek text of each verse in [range], word by word. */
    suspend fun original(range: VerseRange): Map<VerseId, OriginalVerse> = withContext(io) {
        q.origRange(range.start.value.toLong(), range.end.value.toLong()).executeAsList().associate {
            val id = VerseId(it.verse_id.toInt())
            id to OriginalVerse(id, Language.of(it.lang), OriginalWord.decodeVerse(it.lang, it.words))
        }
    }

    suspend fun original(verse: VerseId): OriginalVerse? = original(VerseRange.single(verse))[verse]

    /** Plain-language explanations of grammar codes. */
    suspend fun morph(codes: Collection<String>): Map<String, MorphInfo> = withContext(io) {
        if (codes.isEmpty()) return@withContext emptyMap()
        q.morphByCodes(codes.distinct()).executeAsList().associate { it.code to MorphInfo(it.code, it.parsing, it.summary, it.explanation, it.example) }
    }

    /** In how many KJV verses each STEP sense occurs in the original text. */
    suspend fun senseCounts(ids: Collection<String>): Map<String, Int> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyMap()
        q.senseCounts(ids.distinct()).executeAsList().associate { it.id to it.verses.toInt() }
    }

    /** The people, places or other names that STEP ids (e.g. "H0085") refer to. */
    suspend fun entitiesFor(stepIds: Collection<String>): Map<String, BibleEntity> = withContext(io) {
        if (stepIds.isEmpty()) return@withContext emptyMap()
        q.entityForStepIds(stepIds.distinct()).executeAsList().associate { r ->
            r.step_id to BibleEntity(r.id.toInt(), r.name, r.kind, r.type, r.description, r.summary, r.tribe, r.area, r.lat, r.lon, r.verses.toInt(), r.unique_name)
        }
    }

    suspend fun entity(id: Int): BibleEntity? = withContext(io) {
        q.entityById(id.toLong()).executeAsOneOrNull()?.let { r ->
            BibleEntity(r.id.toInt(), r.name, r.kind, r.type, r.description, r.summary, r.tribe, r.area, r.lat, r.lon, r.verses.toInt(), r.unique_name)
        }
    }

    suspend fun entityNames(id: Int): List<EntityNameForm> = withContext(io) {
        q.entityNames(id.toLong()).executeAsList().map { EntityNameForm(it.step_id, it.significance, it.original, it.translated) }
    }

    /** Family and place links, grouped by relation ("parent", "sibling", "partner", "child", "founder", "resident"). */
    suspend fun entityLinks(id: Int): Map<String, List<BibleEntity>> = withContext(io) {
        q.entityLinks(id.toLong()).executeAsList().groupBy({ it.rel }) { r ->
            BibleEntity(r.id.toInt(), r.name, r.kind, r.type, r.description, r.summary, r.tribe, r.area, r.lat, r.lon, r.verses.toInt(), r.unique_name)
        }
    }

    suspend fun entityRefs(id: Int, after: VerseId? = null, limit: Int = 40): List<VerseId> = withContext(io) {
        q.entityRefs(id.toLong(), (after?.value ?: 0).toLong(), limit.toLong()).executeAsList().map { VerseId(it.toInt()) }
    }

    /** Slugs of VerseUp stories that mention the entity. */
    suspend fun entityStories(id: Int): List<String> = withContext(io) { q.entityStories(id.toLong()).executeAsList() }

    /** People, places and other names starting with [prefix] (folded), most mentioned first. */
    suspend fun entitiesByName(prefix: String, limit: Int = 12): List<BibleEntity> = withContext(io) {
        val p = Fold.fold(prefix.trim())
        if (p.isEmpty()) return@withContext emptyList()
        q.entitiesByName(p, p + "\uFFFF", limit.toLong()).executeAsList().map { r ->
            BibleEntity(r.id.toInt(), r.name, r.kind, r.type, r.description, r.summary, r.tribe, r.area, r.lat, r.lon, r.verses.toInt(), r.unique_name)
        }
    }

    /** Cross-references from [verse], most voted first. */
    suspend fun crossRefs(verse: VerseId, limit: Int = 100): List<CrossReference> = withContext(io) {
        q.crossRefs(verse.value.toLong(), limit.toLong()).executeAsList().map { CrossReference(VerseId(it.to_start.toInt()), VerseId(it.to_end.toInt()), it.votes.toInt()) }
    }

    suspend fun renderings(num: Int, limit: Int = 40): List<KjvRendering> = withContext(io) {
        q.renderings(num.toLong(), limit.toLong()).executeAsList().map { KjvRendering(it.text, it.count.toInt()) }
    }

    suspend fun distribution(num: Int): List<BookCount> = withContext(io) {
        q.bookDistribution(num.toLong()).executeAsList().map { BookCount(it.book.toInt(), it.verses.toInt()) }
    }

    /** One page of verses tagged with [num], after [after] (exclusive), optionally in one [book]. */
    suspend fun occurrences(num: Int, after: VerseId? = null, limit: Int = 40, book: Int? = null): List<VerseId> = withContext(io) {
        val from = (after?.value ?: 0).toLong()
        val list = if (book == null) q.occurrencePage(num.toLong(), from, limit.toLong()).executeAsList()
        else q.occurrencePageInBook(num.toLong(), from, book * 1_000_000L, book * 1_000_000L + 999_999L, limit.toLong()).executeAsList()
        list.map { VerseId(it.toInt()) }
    }

    /**
     * Searches by Strong's number (exact or partial), Bible reference, Hebrew or Greek (accents and vowel points
     * ignored), transliteration, gloss, definition, and the English words the KJV uses.
     */
    suspend fun search(input: String, limit: Int = 30): StudySearchResult = withContext(io) {
        val text = input.trim()
        if (text.isEmpty()) return@withContext StudySearchResult()
        try {
            searchNow(text, limit)
        } catch (e: Exception) {
            // A search problem must never take the app down.
            StudySearchResult(text)
        }
    }

    private fun searchNow(text: String, limit: Int): StudySearchResult {
        val strongs = LinkedHashMap<Int, StrongsEntry>()
        val step = LinkedHashMap<String, StepEntry>()

        // 1. A Strong's number: exact ("G26", "H0430", "H1254a"), then partial ("G2" also finds G20-G29 ...).
        StrongsNumber.parse(text)?.let { num ->
            q.strongsByNum(num.toLong()).executeAsOneOrNull()?.let { strongs[num] = it.toEntry() }
            val letter = StrongsNumber.senseLetter(text)
            val senses = q.stepByNum(num.toLong()).executeAsList().map { it.toStep() }
            senses.filter { letter == null || it.id.last().uppercaseChar() == letter }.forEach { step[it.id] = it }
            val base = num % StrongsNumber.GREEK
            if (base < 1000 && letter == null) {
                val lang = if (StrongsNumber.isGreek(num)) 'G' else 'H'
                val partial = (0..9).map { StrongsNumber.of(lang, base * 10 + it) } +
                    (0..99).map { StrongsNumber.of(lang, base * 100 + it) }
                q.strongsByNums(partial.map { it.toLong() }).executeAsList().map { it.toEntry() }
                    .sortedBy { it.num }.take(limit).forEach { strongs.putIfAbsent(it.num, it) }
            }
            return StudySearchResult(text, strongs.values.toList(), step.values.toList())
        }

        val reference = BibleSearch.parseReference(text)
        val folded = Fold.fold(text)
        val terms = TERM.findAll(folded).map { it.value }.toList()
        if (terms.isEmpty()) return StudySearchResult(text, reference = reference)

        // 2. The lexicons: lemma or transliteration first, then glosses, then definitions.
        // FTS4 column filters apply to one term each, so every term carries its column.
        fun column(c: String, prefix: Boolean) = terms.joinToString(" ") { "$c:$it" + if (prefix) "*" else "" }
        val passes = listOf(
            column("lemma", false), column("xlit", false), column("ids", false),
            column("lemma", true), column("xlit", true),
            column("gloss", false), column("gloss", true), column("defs", false),
        )
        for (match in passes) {
            if (strongs.size + step.size >= limit) break
            for ((kind, ref) in ftsDocs(match, limit * 2)) {
                when (kind) {
                    "strongs" -> ref.toIntOrNull()?.let { n -> if (n !in strongs) q.strongsByNum(n.toLong()).executeAsOneOrNull()?.let { strongs[n] = it.toEntry() } }
                    "step" -> if (ref !in step) q.stepById(ref).executeAsOneOrNull()?.let { step[ref] = it.toStep() }
                }
            }
        }

        // A STEP sense also brings its traditional Strong's entry, so both studies are one tap away.
        val bases = step.values.map { it.num }.filter { it > 0 && it !in strongs }.distinct()
        if (bases.isNotEmpty()) q.strongsByNums(bases.map { it.toLong() }).executeAsList().map { it.toEntry() }
            .forEach { strongs.putIfAbsent(it.num, it) }

        // 3. English: what the KJV translates with this word (only for Latin-script queries).
        val english = ArrayList<EnglishHit>()
        if (!Fold.isHebrew(text) && !Fold.isGreek(text) && terms.size == 1) {
            val word = terms[0]
            var rows = q.englishWord(word, 12).executeAsList().map { Triple(word, it.num.toInt(), it.count.toInt()) }
            if (rows.isEmpty() && word.length >= 3) {
                rows = q.englishPrefix(word, word + "￿", 12).executeAsList().map { Triple(it.word, it.num.toInt(), it.count.toInt()) }
            }
            val entries = if (rows.isEmpty()) emptyMap()
            else q.strongsByNums(rows.map { it.second.toLong() }).executeAsList().map { it.toEntry() }.associateBy { it.num }
            // One row per Strong's number ("lovingkindness" and "lovingkindnesses" are the same word).
            rows.groupBy { it.second }.forEach { (n, list) ->
                entries[n]?.let { english += EnglishHit(list.maxBy { it.third }.first, it, list.sumOf { it.third }) }
            }
            english.sortByDescending { it.count }
        }

        // 4. People, places and other names, by their English name (all words of the query, as typed).
        val entities = if (Fold.isHebrew(text) || Fold.isGreek(text) || reference != null) emptyList()
        else q.entitiesByName(folded.trim(), folded.trim() + "\uFFFF", 8).executeAsList().map { r ->
            BibleEntity(r.id.toInt(), r.name, r.kind, r.type, r.description, r.summary, r.tribe, r.area, r.lat, r.lon, r.verses.toInt(), r.unique_name)
        }

        val result = StudySearchResult(
            query = text,
            entities = entities,
            strongs = strongs.values.sortedWith(compareByDescending<StrongsEntry> { lemmaMatches(it, terms) }.thenByDescending { it.verses }).take(limit),
            step = step.values.take(limit),
            english = english,
            reference = reference,
        )
        if (!result.isEmpty || terms.size != 1) return result
        return result.copy(suggestions = suggest(terms[0]))
    }

    private fun lemmaMatches(e: StrongsEntry, terms: List<String>): Boolean {
        val l = Fold.fold(e.lemma); val x = Fold.fold(e.xlit ?: "")
        return terms.any { it == l || it == x }
    }

    private fun ftsDocs(match: String, limit: Int): List<Pair<String, String>> = driver.executeQuery(
        null,
        "SELECT d.kind, d.ref FROM lexicon_fts f JOIN search_doc d ON d.id = f.docid WHERE lexicon_fts MATCH ? LIMIT ?",
        { c ->
            val out = ArrayList<Pair<String, String>>()
            while (c.next().value) out += c.getString(0)!! to c.getString(1)!!
            QueryResult.Value(out)
        },
        2,
    ) { bindString(0, match); bindLong(1, limit.toLong()) }.value

    /** English words a letter or two away from [word] (typing mistakes and old spellings). */
    private fun suggest(word: String): List<String> {
        if (word.length < 3) return emptyList()
        val start = word.take(1)
        val candidates = q.englishPrefix(start, start + "￿", 4000).executeAsList().map { it.word }.distinct()
        return candidates.map { it to distance(it, word) }.filter { it.second in 1..2 }
            .sortedBy { it.second }.map { it.first }.distinct().take(3)
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 99
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun Strongs.toEntry() = StrongsEntry(
        num = num.toInt(), language = Language.of(lang), lemma = lemma, xlit = xlit, pron = pron, partOfSpeech = pos,
        derivation = derivation, definition = definition, kjvDefinition = kjv_def, sourceId = source_id,
        verses = verses.toInt(), words = words.toInt(),
    )

    private fun Step_entry.toStep() = StepEntry(
        id = id, num = num.toInt(), eStrong = estrong, uStrong = ustrong, label = label, language = Language.of(lang),
        lemma = lemma, xlit = xlit, morph = morph, gloss = gloss, meaning = meaning, sourceId = source_id,
    )

    companion object {
        /** Words of a folded query, in any script. */
        private val TERM = Regex("[\\p{L}\\p{N}]+")
    }
}
