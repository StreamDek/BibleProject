package app.bible.app.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bible.app.data.BibleRepository
import app.bible.app.data.VerseText
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.feed.CardSpan
import app.bible.feed.Density
import app.bible.feed.VerseGrouper
import app.bible.feed.VerseUnit
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
    val reference: String get() = VerseRange(first, last).display()
}

data class ReadingPrefs(
    val density: Density = Density.Default,
    val fontScale: Float = 1f,
    val dark: Boolean = true,
)

data class FeedUiState(
    val loading: Boolean = true,
    val bookNumber: Int = 1,
    val cards: List<FeedCard> = emptyList(),
    val initialPage: Int = 0,
    val prefs: ReadingPrefs = ReadingPrefs(),
    /** Bumped whenever the card list is rebuilt, so the pager re-keys and scrolls to [initialPage]. */
    val generation: Int = 0,
)

/**
 * Free-reading feed over one book (CanonicalSource). Swiping continues across chapters; cards never cross
 * a chapter. The reading position is the anchor verse, not a page index, so changing density keeps the place.
 */
class FeedViewModel(private val bible: BibleRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var units: List<VerseUnit> = emptyList()
    private var anchor: VerseId = VerseId.of(43, 1, 1) // John 1:1 for the first launch
    private val textCache = LinkedHashMap<Int, List<VerseText>>()

    init { open(anchor) }

    /** Opens the book containing [verse] and positions the feed on the card that contains it. */
    fun open(verse: VerseId) {
        anchor = verse
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            units = bible.units(VerseRange.book(verse.book))
            textCache.clear()
            rebuild()
        }
    }

    fun onPageSettled(page: Int) {
        state.value.cards.getOrNull(page)?.let { anchor = it.first }
    }

    fun setDensity(density: Density) {
        _state.update { it.copy(prefs = it.prefs.copy(density = density)) }
        rebuild()
    }

    fun setFontScale(scale: Float) {
        _state.update { it.copy(prefs = it.prefs.copy(fontScale = scale.coerceIn(0.8f, 1.6f))) }
        rebuild() // larger type fits fewer characters per card
    }

    fun toggleDark() = _state.update { it.copy(prefs = it.prefs.copy(dark = !it.prefs.dark)) }

    suspend fun textFor(card: FeedCard): List<VerseText> =
        textCache[card.key] ?: bible.verses(card.first, card.last).also {
            textCache[card.key] = it
            if (textCache.size > 40) textCache.remove(textCache.keys.first())
        }

    suspend fun heading(card: FeedCard): String? =
        if (card.first.verse == 1) bible.heading(card.first.book, card.first.chapter) else null

    private fun rebuild() {
        if (units.isEmpty()) return
        textCache.clear() // cards change shape when density or font size changes
        val prefs = _state.value.prefs
        val base = (VerseGrouper.DEFAULT_CHAR_BUDGET / (prefs.fontScale * prefs.fontScale)).toInt()
        val spans: List<CardSpan> = VerseGrouper.forDensity(prefs.density, base).group(units, prefs.density)
        val cards = spans.map { s ->
            FeedCard(units[s.first].id.value, units[s.first].id, units[s.last].id, s.continues)
        }
        val page = cards.indexOfFirst { anchor >= it.first && anchor <= it.last }.coerceAtLeast(0)
        _state.update {
            it.copy(
                loading = false,
                bookNumber = anchor.book,
                cards = cards,
                initialPage = page,
                generation = it.generation + 1,
            )
        }
    }

    val books get() = Canon.books
}
