package app.bible.app.study

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bible.app.data.BibleRepository
import app.bible.app.data.VerseText
import app.bible.app.data.openStudyDriver
import app.bible.app.resources.Res
import app.bible.core.AttributionRegistry
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.bible.SearchResult
import app.bible.data.study.Language
import app.bible.data.study.MorphInfo
import app.bible.data.study.OriginalVerse
import app.bible.data.study.StrongsNumber
import app.bible.data.study.StudyStore
import app.bible.data.study.WordTag
import app.bible.data.user.SavedWord
import app.bible.data.user.StudiedWord
import app.bible.data.user.UserStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Which lexicon the reader studies with. Remembered between sessions. */
enum class StudySource(val key: String, val label: String) {
    Strongs("strongs", "Strong's"), Step("step", "STEP Bible"), Compare("compare", "Compare");

    companion object {
        fun of(key: String?): StudySource = entries.firstOrNull { it.key == key } ?: Strongs
    }
}

/**
 * Where a word study was opened from: the KJV verse and the tapped word or phrase in it ([start] until [end] in
 * the verse text), with the Strong's numbers the KJV's tags give that phrase. [nums] is empty when the word has no
 * tag (for example a word the translators supplied in italics).
 */
data class WordContext(val verse: VerseId, val text: String, val start: Int, val end: Int, val nums: List<Int>) {
    val word: String get() = text.substring(start, end)
}

/** What to open in the word-study sheet: a Strong's number, a STEP sense, or a tapped word from a verse. */
data class WordStudyRequest(
    val num: Int? = null,
    val stepId: String? = null,
    val context: WordContext? = null,
    /** Open the Hebrew or Greek of this verse, word by word, instead of one word. */
    val interlinear: VerseId? = null,
    /** Open a person, place or other name (STEP Bible TIPNR entity id). */
    val entity: Int? = null,
    /** Open the cross-references of this verse. */
    val crossRefs: VerseId? = null,
)

/** VerseUp's stories as the study screens need them: a title for a slug, and opening one. Provided once in App. */
class StoryLinksForStudy(val title: (String) -> String?, val open: (String) -> Unit)

val LocalStudyStories = staticCompositionLocalOf<StoryLinksForStudy?> { null }

/** Opens the word-study sheet from anywhere in the app. Provided once in App. */
val LocalWordStudy = staticCompositionLocalOf<(WordStudyRequest) -> Unit> { {} }

/** The app's study state, for screens that show tappable words. */
val LocalStudy = staticCompositionLocalOf<StudyViewModel?> { null }

/**
 * Word study: Strong's and STEP Bible lexicons, the KJV's word tags, saved words and recently studied words.
 * study.db is opened on first use, not at start-up, so it never slows down opening the app.
 */
class StudyViewModel(private val bible: BibleRepository, private val user: UserStore) : ViewModel() {
    private val storeOpen: Deferred<StudyStore> = viewModelScope.async(start = CoroutineStart.LAZY) { StudyStore(openStudyDriver()) }

    /** The study pack, opened the first time it is needed. */
    suspend fun store(): StudyStore = storeOpen.await()

    private val _source = MutableStateFlow(StudySource.Strongs)
    val source: StateFlow<StudySource> = _source.asStateFlow()

    private val _saved = MutableStateFlow<List<SavedWord>>(emptyList())
    val saved: StateFlow<List<SavedWord>> = _saved.asStateFlow()

    private val _recent = MutableStateFlow<List<StudiedWord>>(emptyList())
    val recent: StateFlow<List<StudiedWord>> = _recent.asStateFlow()

    private val _registry = MutableStateFlow<AttributionRegistry?>(null)
    val registry: StateFlow<AttributionRegistry?> = _registry.asStateFlow()

    init {
        viewModelScope.launch { _source.value = StudySource.of(user.setting(SOURCE_KEY)) }
        viewModelScope.launch {
            user.studyRevision.collect {
                _saved.value = user.savedWords()
                _recent.value = user.recentStudies(20)
            }
        }
    }

    fun setSource(source: StudySource) {
        _source.value = source
        viewModelScope.launch { user.putSetting(SOURCE_KEY, source.key) }
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun registry(): AttributionRegistry {
        _registry.value?.let { return it }
        val r = AttributionRegistry.decode(Res.readBytes("files/attributions.json").decodeToString())
        _registry.value = r
        return r
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun licenceText(file: String): String = Res.readBytes("files/$file").decodeToString()

    suspend fun verse(id: VerseId): VerseText? = bible.verses(id, id).firstOrNull()

    suspend fun verses(ids: List<VerseId>): Map<VerseId, VerseText> = ids.mapNotNull { verse(it) }.associateBy { it.id }

    suspend fun tags(verse: VerseId): List<WordTag> = store().tags(VerseRange.single(verse))[verse] ?: emptyList()

    suspend fun tags(ids: List<VerseId>): Map<VerseId, List<WordTag>> {
        if (ids.isEmpty()) return emptyMap()
        val s = store()
        // Pages of occurrences are sorted, so one range query covers them; keep only the verses asked for.
        val wanted = ids.toSet()
        return s.tags(VerseRange(ids.min(), ids.max())).filterKeys { it in wanted }
    }

    suspend fun original(verse: VerseId): OriginalVerse? = runCatching { store().original(verse) }.getOrNull()

    suspend fun morph(codes: Collection<String>): Map<String, MorphInfo> = runCatching { store().morph(codes) }.getOrDefault(emptyMap())

    /**
     * The KJV word a Strong's number is tagged on in [verse], as a study context, so a word opened from the Hebrew
     * or Greek still shows where it is in the English.
     */
    suspend fun contextFor(verse: VerseId, num: Int): WordContext? {
        if (num <= 0) return null
        val text = verse(verse)?.text ?: return null
        val tag = tags(verse).firstOrNull { num in it.nums } ?: return null
        return WordContext(verse, text, tag.start, tag.end, tag.nums)
    }

    suspend fun searchScripture(query: String): SearchResult = bible.search(query)

    fun isSaved(entry: String): Boolean = _saved.value.any { it.entry == entry }

    fun toggleSaved(entry: String, source: StudySource, lang: Language, lemma: String, gloss: String, verse: VerseId?) {
        viewModelScope.launch {
            if (user.savedWord(entry) != null) user.removeSavedWord(entry)
            else user.saveWord(SavedWord(entry, if (source == StudySource.Step) "step" else "strongs", lang.code, lemma, gloss, verse, "", false, 0))
        }
    }

    fun updateSaved(word: SavedWord) { viewModelScope.launch { user.saveWord(word) } }

    fun removeSaved(entry: String) { viewModelScope.launch { user.removeSavedWord(entry) } }

    fun recordStudy(num: Int, lang: Language, lemma: String, gloss: String, verse: VerseId?) {
        viewModelScope.launch { user.recordStudy(StrongsNumber.id(num), lang.code, lemma, gloss, verse) }
    }

    companion object {
        const val SOURCE_KEY = "study.source"

        /**
         * Word studies offered on the Study page. Every one is a real Strong's number; what the cards show (headword,
         * transliteration, gloss) is read from study.db, never written here.
         */
        val SUGGESTED_HEBREW = listOf(430, 1254, 7307, 2617, 7965, 539, 6944, 3068, 8451, 1285)
        val SUGGESTED_PEOPLE = listOf("Abraham", "Moses", "Ruth", "David", "Esther", "Peter", "Paul", "Jerusalem", "Bethlehem", "Egypt", "Jordan", "Babylon")
        val SUGGESTED_GREEK = listOf(26, 5485, 4102, 3056, 1680, 1515, 3341, 5547, 4151, 2098).map { StrongsNumber.of('G', it) }
    }
}
