package app.bible.pipeline

import app.bible.core.Canon
import app.bible.data.bible.BibleDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.security.MessageDigest

enum class Translation(val code: String, val title: String, val license: String, val attribution: String) {
    KJV(
        code = "kjv",
        title = "King James Version",
        license = "public-domain",
        attribution = "King James Version (1769 standard text). Public domain. Source: eBible.org.",
    ),
}

object BibleDbWriter {
    /** Bump when the bible-<translation>.db schema changes. The app refuses packs with an unknown version. */
    const val SCHEMA_VERSION = 2

    fun write(bible: ImportedBible, out: File, translation: Translation): String {
        val verses = bible.verses
        check(verses.size == Canon.totalVerses) { "Expected ${Canon.totalVerses} verses, got ${verses.size}" }
        val missing = Canon.books.flatMap { b -> (1..b.chapterCount).flatMap { c -> (1..b.verseCount(c)).map { v -> Triple(b, c, v) } } }
            .map { (b, c, v) -> app.bible.core.VerseId.of(b.number, c, v) }
            .toSet() - verses.map { it.id }.toSet()
        check(missing.isEmpty()) { "Missing verses: ${missing.take(10)}" }

        out.parentFile.mkdirs()
        if (out.exists()) out.delete()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}")
        BibleDatabase.Schema.create(driver)
        val db = BibleDatabase(driver)
        val q = db.bibleQueries
        db.transaction {
            for (v in verses) {
                val flags = (if (v.paragraphStart) 1 else 0) or (if (v.poetry) 2 else 0)
                q.insertVerse(v.id.value.toLong(), v.text, spans(v.italics), spans(v.red), flags.toLong(), v.weightAfter.toLong())
            }
            for ((id, title) in bible.sections.toSortedMap()) q.insertSection(id.value.toLong(), title)
            for ((chapterId, text) in bible.headings.toSortedMap()) q.insertHeading(chapterId.toLong(), "psalm", text)
            val textHash = sha256(verses.joinToString("\n") { "${it.id.value}\t${it.text}" })
            listOf(
                "schema_version" to "$SCHEMA_VERSION",
                "translation" to translation.code,
                "title" to translation.title,
                "license" to translation.license,
                "attribution" to translation.attribution,
                "versification" to "kjv",
                "verse_count" to "${verses.size}",
                // Integrity check: hash of every verse id + text. Lets the app and CI detect any change to Scripture text.
                "text_sha256" to textHash,
            ).forEach { (k, v) -> q.insertMeta(k, v) }
        }
        // Full-text search over the verse text (FTS5, external content: no duplicate copy of the text).
        driver.execute(null, "CREATE VIRTUAL TABLE verse_fts USING fts5(text, content='verse', content_rowid='id', tokenize='unicode61 remove_diacritics 2')", 0)
        driver.execute(null, "INSERT INTO verse_fts(verse_fts) VALUES('rebuild')", 0)
        driver.execute(null, "INSERT INTO verse_fts(verse_fts) VALUES('optimize')", 0)
        // user_version must equal the SQLDelight schema version, or platform drivers try to migrate the file.
        // The pack format version lives in meta.schema_version instead.
        driver.execute(null, "PRAGMA user_version = ${BibleDatabase.Schema.version}", 0)
        driver.close()
        // Compact the file.
        JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}").use { it.execute(null, "VACUUM", 0) }

        val paragraphs = verses.count { it.paragraphStart }
        val italicSpans = verses.sumOf { it.italics.size }
        val redVerses = verses.count { it.red.isNotEmpty() }
        val weights = verses.groupingBy { it.weightAfter }.eachCount().toSortedMap()
        return "Wrote ${out.path}: ${verses.size} verses, $paragraphs paragraph starts, ${bible.headings.size} psalm titles, " +
            "$italicSpans italic spans, $redVerses verses with words of Jesus, ${bible.sections.size} sections, boundary weights $weights, ${out.length() / 1024} KB"
    }

    private fun spans(list: List<IntRange>): String? =
        list.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.first}:${it.last + 1}" }

    /**
     * Writes the identity of a built database (pack schema version + hash of the file) next to it. The app compares
     * this small file with the stamp of its installed copy, so it only re-copies the database after it changed.
     */
    fun writeStamp(db: File, stamp: File) {
        val digest = MessageDigest.getInstance("SHA-256").digest(db.readBytes()).joinToString("") { "%02x".format(it) }
        stamp.writeText("$SCHEMA_VERSION-${digest.take(24)}\n")
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Compact grouper fixture: "verseId<TAB>chars<TAB>weightAfter" per line. Used by feed-engine golden tests. */
object UnitsFixture {
    fun write(bible: ImportedBible, out: File) {
        out.parentFile.mkdirs()
        out.bufferedWriter().use { w ->
            for (v in bible.verses) w.append("${v.id.value}\t${v.text.length}\t${v.weightAfter}\n")
        }
    }
}
