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
    const val SCHEMA_VERSION = 1

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
                val italics = v.italics.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.first}:${it.last + 1}" }
                q.insertVerse(v.id.value.toLong(), v.text, italics, flags.toLong(), v.weightAfter.toLong())
            }
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
        driver.execute(null, "PRAGMA user_version = $SCHEMA_VERSION", 0)
        driver.close()
        // Compact the file.
        JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}").use { it.execute(null, "VACUUM", 0) }

        val paragraphs = verses.count { it.paragraphStart }
        val italicSpans = verses.sumOf { it.italics.size }
        val weights = verses.groupingBy { it.weightAfter }.eachCount().toSortedMap()
        return "Wrote ${out.path}: ${verses.size} verses, $paragraphs paragraph starts, ${bible.headings.size} psalm titles, " +
            "$italicSpans italic spans, boundary weights $weights, ${out.length() / 1024} KB"
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
