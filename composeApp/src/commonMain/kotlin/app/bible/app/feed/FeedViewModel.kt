package app.bible.app.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bible.app.data.BibleRepository
import app.bible.app.data.VerseText
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.bible.SearchResult
import app.bible.data.user.SavedNote
import app.bible.data.user.UserStore
import app.bible.data.user.VerseMarks
import app.bible.feed.CardSpan
import app.bible.feed.Density
import app.bible.feed.VerseGrouper
import app.bible.feed.VerseUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page of the feed. Text is loaded lazily per card. */
data class FeedCard(
    val key: Int,             // first verse id: stable identity for the pager
    val first: VerseId,
    val last: VerseId,
    val continues: Boolean,
) {
    val range: VerseRange get() = VerseRange(first, last)
    val reference: String get() = range.display()
}

data class ReadingPrefs(
    val density: Density = Density.Default,
    val fontScale: Float = 1f,
    val dark: Boolean = true,
    /** Show the words of Jesus in red. */
    val redLetters: Boolean = true,
    /** Hide the status bar for a full-screen reading view. */
    val immersive: Boolean = false,
    /** In stories, show the story's image, blurred and dimmed, behind the text. */
    val storyArtBackground: Boolean = false,
)

enum class ReaderMode { Feed, Classic }

data class FeedUiState(
    val loading: Boolean = true,
    val cards: List<FeedCard> = emptyList(),
    /** Card indices of each book (book number -> first..last card), for the progress bar. */
    val bookCards: Map<Int, IntRange> = emptyMap(),
    val initialPage: Int = 0,
    val prefs: ReadingPrefs = ReadingPrefs(),
    val mode: ReaderMode = ReaderMode.Feed,
    /** Where the reader is: the first verse of the current card (or the top verse in the classic reader). */
    val anchor: VerseId = FIRST_LAUNCH_VERSE,
    /** Bumped whenever the card list is rebuilt, so the pager re-keys and scrolls to [initialPage]. */
    val generation: Int = 0,
)

/** John 1:1 for the very first launch. */
val FIRST_LAUNCH_VERSE: VerseId = VerseId.of(43, 1, 1)

/**
 * Free reading over the whole Bible (CanonicalSource): swiping runs from Genesis to Revelation, and cards never
 * cross a chapter or section. The reading position is a verse, not a page index, so changing density or text
 * size keeps the place, and it is saved on the device so the app reopens where the reader left off.
 */
class FeedViewModel(private val bible: BibleRepository, private val user: UserStore) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** Bumped when highlights, bookmarks or notes change. */
    val marksRevision: StateFlow<Int> = user.revision

    private var units: List<VerseUnit> = emptyList()
    private val textCache = LinkedHashMap<VerseRange, List<VerseText>>()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = loadPrefs()
            val anchor = user.position() ?: FIRST_LAUNCH_VERSE
            _state.update { it.copy(prefs = prefs, anchor = anchor) }
            units = bible.units(VerseRange(Canon.books.first().firstVerse, Canon.books.last().lastVerse))
            rebuild()
        }
    }

    /** Moves the reader to [verse] (from Jump, search, saved items or the classic reader). */
    fun open(verse: VerseId) {
        _state.update { it.copy(anchor = verse) }
        rebuild()
        savePositionSoon(0)
    }

    fun onPageSettled(page: Int) {
        val card = state.value.cards.getOrNull(page) ?: return
        if (card.first == state.value.anchor) return
        // initialPage follows the reader, so the feed reopens on this card after visiting another screen.
        _state.update { it.copy(anchor = card.first, initialPage = page) }
        savePositionSoon()
    }

    fun setMode(mode: ReaderMode) = _state.update { it.copy(mode = mode) }

    /** The classic reader reports the verse at the top of the screen. */
    fun onClassicPosition(verse: VerseId) {
        if (verse == state.value.anchor) return
        _state.update { it.copy(anchor = verse) }
        savePositionSoon()
    }

    /** Leaves the classic reader, opening the feed on the verse it was showing. */
    fun closeClassic() {
        _state.update { it.copy(mode = ReaderMode.Feed) }
        rebuild()
    }

    // ---- reading settings ---------------------------------------------------------------------------

    fun setDensity(density: Density) {
        updatePrefs { it.copy(density = density) }
        rebuild()
    }

    fun setFontScale(scale: Float) {
        updatePrefs { it.copy(fontScale = scale.coerceIn(0.8f, 1.6f)) }
        rebuild() // larger type fits fewer characters per card
    }

    fun toggleDark() = updatePrefs { it.copy(dark = !it.dark) }

    fun setRedLetters(on: Boolean) = updatePrefs { it.copy(redLetters = on) }

    fun setImmersive(on: Boolean) = updatePrefs { it.copy(immersive = on) }

    fun setStoryArtBackground(on: Boolean) = updatePrefs { it.copy(storyArtBackground = on) }

    private fun updatePrefs(change: (ReadingPrefs) -> ReadingPrefs) {
        _state.update { it.copy(prefs = change(it.prefs)) }
        val p = state.value.prefs
        viewModelScope.launch {
            user.putSetting(KEY_DENSITY, when (val d = p.density) { is Density.Fixed -> "${d.verses}"; Density.Auto -> "auto" })
            user.putSetting(KEY_FONT_SCALE, "${p.fontScale}")
            user.putSetting(KEY_DARK, "${p.dark}")
            user.putSetting(KEY_RED_LETTERS, "${p.redLetters}")
            user.putSetting(KEY_IMMERSIVE, "${p.immersive}")
            user.putSetting(KEY_STORY_ART_BG, "${p.storyArtBackground}")
        }
    }

    private suspend fun loadPrefs(): ReadingPrefs {
        val d = ReadingPrefs()
        val density = when (val s = user.setting(KEY_DENSITY)) {
            null -> d.density
            "auto" -> Density.Auto
            else -> s.toIntOrNull()?.let { n -> Density.Options.firstOrNull { it == Density.Fixed(n) } } ?: d.density
        }
        return ReadingPrefs(
            density = density,
            fontScale = user.setting(KEY_FONT_SCALE)?.toFloatOrNull()?.coerceIn(0.8f, 1.6f) ?: d.fontScale,
            dark = user.setting(KEY_DARK)?.toBooleanStrictOrNull() ?: d.dark,
            redLetters = user.setting(KEY_RED_LETTERS)?.toBooleanStrictOrNull() ?: d.redLetters,
            immersive = user.setting(KEY_IMMERSIVE)?.toBooleanStrictOrNull() ?: d.immersive,
            storyArtBackground = user.setting(KEY_STORY_ART_BG)?.toBooleanStrictOrNull() ?: d.storyArtBackground,
        )
    }

    // ---- content ------------------------------------------------------------------------------------

    suspend fun textFor(card: FeedCard): List<VerseText> =
        textCache[card.range] ?: bible.verses(card.first, card.last).also {
            textCache[card.range] = it
            if (textCache.size > 40) textCache.remove(textCache.keys.first())
        }

    private val chapterCache = LinkedHashMap<Int, List<VerseText>>()

    /** All verses of a chapter (for the classic reader and verse actions). */
    suspend fun chapterText(book: Int, chapter: Int): List<VerseText> {
        val key = book * 1_000 + chapter
        return chapterCache[key] ?: VerseRange.chapter(book, chapter).let { r -> bible.verses(r.start, r.end) }.also {
            chapterCache[key] = it
            if (chapterCache.size > 12) chapterCache.remove(chapterCache.keys.first())
        }
    }

    /** Psalm superscription when the card opens a psalm. */
    suspend fun heading(card: FeedCard): String? =
        if (card.first.verse == 1) bible.heading(card.first.book, card.first.chapter) else null

    /** Our section heading when the card starts a section. */
    suspend fun sectionTitle(card: FeedCard): String? = bible.sections(VerseRange.single(card.first))[card.first]

    suspend fun sections(range: VerseRange): Map<VerseId, String> = bible.sections(range)

    suspend fun psalmTitle(book: Int, chapter: Int): String? = bible.heading(book, chapter)

    suspend fun marks(range: VerseRange): VerseMarks = user.marks(range)

    suspend fun search(query: String): SearchResult = bible.search(query)

    suspend fun bookmarks(): List<VerseId> = user.bookmarks()

    suspend fun notes(): List<SavedNote> = user.notes()

    suspend fun verse(id: VerseId): VerseText? = bible.verses(id, id).firstOrNull()

    // ---- verse actions ------------------------------------------------------------------------------

    fun highlight(verses: Collection<VerseId>, color: Int?) {
        viewModelScope.launch { user.setHighlight(verses, color) }
    }

    fun toggleBookmark(verse: VerseId) {
        viewModelScope.launch { user.toggleBookmark(verse) }
    }

    fun saveNote(verse: VerseId, body: String) {
        viewModelScope.launch { user.setNote(verse, body) }
    }

    // ---- internals ----------------------------------------------------------------------------------

    private fun savePositionSoon(delayMs: Long = 600) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(delayMs)
            user.savePosition(state.value.anchor)
        }
    }

    private fun rebuild() {
        if (units.isEmpty()) return
        textCache.clear() // cards change shape when density or font size changes
        val prefs = _state.value.prefs
        val spans: List<CardSpan> = grouperFor(prefs).group(units, prefs.density)
        val cards = spans.map { s ->
            FeedCard(units[s.first].id.value, units[s.first].id, units[s.last].id, s.continues)
        }
        val bookCards = HashMap<Int, IntRange>()
        var start = 0
        for (i in cards.indices) {
            if (i == cards.lastIndex || cards[i + 1].first.book != cards[i].first.book) {
                bookCards[cards[i].first.book] = start..i
                start = i + 1
            }
        }
        val anchor = _state.value.anchor
        val page = indexOfCard(cards, anchor)
        _state.update {
            it.copy(
                loading = false,
                cards = cards,
                bookCards = bookCards,
                initialPage = page,
                anchor = cards.getOrNull(page)?.first ?: anchor,
                generation = it.generation + 1,
            )
        }
    }

    private fun indexOfCard(cards: List<FeedCard>, verse: VerseId): Int {
        // Cards are in canonical order: binary search for the card containing the verse.
        var lo = 0; var hi = cards.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = cards[mid]
            when {
                verse < c.first -> hi = mid - 1
                verse > c.last -> lo = mid + 1
                else -> return mid
            }
        }
        return lo.coerceIn(0, cards.lastIndex.coerceAtLeast(0))
    }

    companion object {
        /** The card grouper for the reader's density and text size (larger type fits fewer characters). */
        fun grouperFor(prefs: ReadingPrefs): VerseGrouper {
            val base = (VerseGrouper.DEFAULT_CHAR_BUDGET / (prefs.fontScale * prefs.fontScale)).toInt()
            return VerseGrouper.forDensity(prefs.density, base)
        }

        private const val KEY_DENSITY = "reading.density"
        private const val KEY_FONT_SCALE = "reading.font_scale"
        private const val KEY_DARK = "reading.dark"
        private const val KEY_RED_LETTERS = "reading.red_letters"
        private const val KEY_IMMERSIVE = "reading.immersive"
        private const val KEY_STORY_ART_BG = "reading.storyArtBackground"
    }
}
