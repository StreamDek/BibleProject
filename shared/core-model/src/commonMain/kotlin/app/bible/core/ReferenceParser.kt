package app.bible.core

/**
 * Parses human Scripture references into canonical [Passage]s.
 *
 * Supported forms (book names are case-insensitive and accept common aliases/abbreviations):
 *  - "Genesis 3", "Genesis 13-14"                  whole chapters
 *  - "John 3:16", "Luke 2:8-14", "Genesis 1:1-2:3" verses and cross-chapter ranges
 *  - "Matthew 13:24-30,36-43"                      gaps within a chapter
 *  - "Luke 1:26-38; 2:1-20", "Isaiah 7:14; Micah 5:2"   several references
 *  - "Jude 3", "Obadiah"                           single-chapter books
 */
object ReferenceParser {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun parse(input: String): Passage = parseOrError(input).getOrElse { throw it }

    fun parseOrNull(input: String): Passage? = parseOrError(input).getOrNull()

    fun parseOrError(input: String): Result<Passage> = runCatching {
        val ranges = mutableListOf<VerseRange>()
        var currentBook: Book? = null
        for (rawPart in input.split(';')) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            val (book, rest) = splitBook(part) ?: run {
                // "Luke 1:26-38; 2:1-20": a part without a book continues the previous book.
                val b = currentBook ?: throw ParseException("Unknown book in \"$part\"")
                b to part
            }
            currentBook = book
            ranges += parseSpec(book, rest.trim(), input)
        }
        if (ranges.isEmpty()) throw ParseException("Empty reference")
        Passage(ranges)
    }

    private fun parseSpec(book: Book, spec: String, original: String): List<VerseRange> {
        if (spec.isEmpty()) return listOf(VerseRange.book(book.number))
        val out = mutableListOf<VerseRange>()
        var lastChapter: Int? = null   // chapter context for "36-43" after "13:24-30"
        for (rawSeg in spec.split(',')) {
            val seg = rawSeg.trim().replace('–', '-').replace('—', '-').replace(" ", "")
            if (seg.isEmpty()) continue
            val pieces = seg.split('-')
            if (pieces.size > 2) throw ParseException("Bad range \"$seg\" in \"$original\"")
            val a = parsePoint(pieces[0], lastChapter, book, original)
            val range = if (pieces.size == 1) {
                when {
                    a.verse != null -> VerseRange.single(id(book, a.chapter, a.verse, original))
                    else -> chapterRange(book, a.chapter, a.chapter, original)
                }
            } else {
                // The end of a range inherits verse-mode from the start: "2:1-20" ends at verse 20.
                val endCtx = if (a.verse != null) a.chapter else null
                val b = parsePoint(pieces[1], endCtx, book, original)
                when {
                    a.verse == null && b.verse == null -> chapterRange(book, a.chapter, b.chapter, original)
                    a.verse == null -> VerseRange(id(book, a.chapter, 1, original), id(book, b.chapter, b.verse!!, original))
                    b.verse == null -> VerseRange(
                        id(book, a.chapter, a.verse, original),
                        id(book, b.chapter, book.verseCount(b.chapter), original),
                    )
                    else -> VerseRange(id(book, a.chapter, a.verse, original), id(book, b.chapter, b.verse, original))
                }
            }
            if (range.start > range.end) throw ParseException("Range runs backwards in \"$original\"")
            out += range
            lastChapter = if (pieces.size == 1 && a.verse == null) null else range.end.chapter
        }
        return out
    }

    private data class Point(val chapter: Int, val verse: Int?)

    /** "13:24" → (13, 24); "36" → verse 36 of [chapterContext] if set, else chapter 36. */
    private fun parsePoint(s: String, chapterContext: Int?, book: Book, original: String): Point {
        if (':' in s) {
            val (c, v) = s.split(':', limit = 2)
            return Point(num(c, original), num(v, original))
        }
        val n = num(s, original)
        return when {
            chapterContext != null -> Point(chapterContext, n)
            // Single-chapter books: "Jude 3" means verse 3; "Jude 1" means the whole chapter.
            book.isSingleChapter && n > 1 -> Point(1, n)
            else -> Point(n, null)
        }
    }

    private fun chapterRange(book: Book, from: Int, to: Int, original: String): VerseRange {
        if (!book.hasChapter(from) || !book.hasChapter(to)) {
            throw ParseException("${book.name} has no chapter ${if (!book.hasChapter(from)) from else to} (\"$original\")")
        }
        return VerseRange(VerseId.of(book.number, from, 1), VerseId.of(book.number, to, book.verseCount(to)))
    }

    private fun id(book: Book, chapter: Int, verse: Int, original: String): VerseId {
        if (!book.hasChapter(chapter)) throw ParseException("${book.name} has no chapter $chapter (\"$original\")")
        if (verse !in 1..book.verseCount(chapter)) {
            throw ParseException("${book.name} $chapter has no verse $verse (\"$original\")")
        }
        return VerseId.of(book.number, chapter, verse)
    }

    private fun num(s: String, original: String): Int =
        s.toIntOrNull()?.takeIf { it > 0 } ?: throw ParseException("Expected a number, got \"$s\" in \"$original\"")

    /** Splits "1 Samuel 17:4" into (1 Samuel, "17:4"). Longest matching book name wins. */
    private fun splitBook(part: String): Pair<Book, String>? {
        val normalized = part.lowercase().replace('.', ' ').replace(Regex("\\s+"), " ").trim()
        // Book names are letters (possibly with a leading number and spaces); the spec starts at the first digit
        // that follows a letter-run, e.g. "1 samuel 17:4" → name "1 samuel", spec "17:4".
        val m = Regex("^((?:[1-3]|i{1,3})\\s?[a-z][a-z ]*?|[a-z][a-z ]*?)\\s*(\\d.*)?$").find(normalized) ?: return null
        val name = m.groupValues[1].trim()
        val book = BookNames.lookup(name) ?: return null
        return book to m.groupValues[2]
    }
}

/** Book name lookup: full names, OSIS codes, common abbreviations and alternative names. */
object BookNames {
    private val aliases: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        fun add(n: Int, vararg names: String) = names.forEach { m[norm(it)] = n }
        Canon.books.forEach { add(it.number, it.name, it.osis) }
        add(1, "gen", "gn"); add(2, "exo", "ex"); add(3, "lev", "lv"); add(4, "num", "nm"); add(5, "deu", "dt")
        add(6, "jos"); add(7, "jdg", "judg"); add(8, "rut", "rth"); add(9, "1 sa", "1sa", "1 sam", "i samuel", "1st samuel")
        add(10, "2 sa", "2sa", "2 sam", "ii samuel", "2nd samuel"); add(11, "1 ki", "1 kgs", "1kgs", "i kings")
        add(12, "2 ki", "2 kgs", "2kgs", "ii kings"); add(13, "1 ch", "1 chr", "1 chron", "i chronicles")
        add(14, "2 ch", "2 chr", "2 chron", "ii chronicles"); add(16, "neh"); add(17, "est", "esth")
        add(19, "psalm", "ps", "psa", "pss"); add(20, "pro", "prv", "prov"); add(21, "ecc", "eccl", "qoheleth")
        add(22, "song of songs", "song", "sos", "canticles", "song of sol"); add(23, "isa", "is"); add(24, "jer")
        add(25, "lam"); add(26, "eze", "ezk"); add(27, "dan", "dn"); add(28, "hos"); add(29, "joe", "jl")
        add(30, "amo"); add(31, "oba", "ob"); add(32, "jon", "jnh"); add(33, "mic"); add(34, "nah", "nam")
        add(35, "hab"); add(36, "zep", "zeph"); add(37, "hag"); add(38, "zec", "zech"); add(39, "mal")
        add(40, "mat", "mt"); add(41, "mar", "mk", "mrk"); add(42, "luk", "lk"); add(43, "joh", "jn", "jhn")
        add(44, "act", "acts of the apostles"); add(45, "rom", "rm"); add(46, "1 co", "1 cor", "i corinthians")
        add(47, "2 co", "2 cor", "ii corinthians"); add(48, "gal"); add(49, "eph"); add(50, "php", "phil")
        add(51, "col"); add(52, "1 th", "1 thess", "i thessalonians"); add(53, "2 th", "2 thess", "ii thessalonians")
        add(54, "1 ti", "1 tim", "i timothy"); add(55, "2 ti", "2 tim", "ii timothy"); add(56, "tit")
        add(57, "phm", "philem"); add(58, "heb"); add(59, "jas", "jam"); add(60, "1 pe", "1 pet", "i peter")
        add(61, "2 pe", "2 pet", "ii peter"); add(62, "1 jn", "1 jo", "i john"); add(63, "2 jn", "ii john")
        add(64, "3 jn", "iii john"); add(65, "jud"); add(66, "rev", "re", "revelation of john", "revelations", "apocalypse")
        m
    }

    private fun norm(s: String) = s.lowercase().replace('.', ' ').replace(Regex("\\s+"), " ").trim()
        .replace(Regex("^([1-3])\\s?"), "$1 ")

    fun lookup(name: String): Book? = aliases[norm(name)]?.let { Canon.book(it) }
}
