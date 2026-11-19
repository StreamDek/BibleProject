package app.bible.app.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bible.app.data.BibleRepository
import app.bible.app.feed.FeedViewModel
import app.bible.app.feed.ReadingPrefs
import app.bible.app.resources.Res
import app.bible.core.VerseId
import app.bible.data.user.UserStore
import app.bible.stories.Story
import app.bible.stories.StoryCatalogue
import app.bible.stories.StoryLibrary
import app.bible.stories.StoryPage
import app.bible.stories.StoryPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class StoriesHomeState(
    val library: StoryLibrary? = null,
    /** The story the reader opened last, for the Continue card. */
    val last: Story? = null,
    val finished: Set<String> = emptySet(),
)

data class StoryReaderState(
    val story: Story? = null,
    val pages: List<StoryPage> = emptyList(),
    /** The chosen account (tab) for each step with parallel accounts. */
    val choices: Map<Int, Int> = emptyMap(),
    /** The page the reader is on; the pager opens here. */
    val page: Int = 0,
    /** Bumped when pages are rebuilt, so the pager re-keys and opens on [page]. */
    val generation: Int = 0,
    val loading: Boolean = false,
)

/**
 * Story Mode (StorySource, proposal §3.2): discovery over the bundled catalogue and the story reader. Stories hold
 * references only; the text is read from the Bible database through the same feed and cards as free reading.
 * Progress is kept in user.db: the position as a verse (source "story:<slug>"), plus the chosen tabs, the last
 * story and the finished stories as settings.
 */
class StoriesViewModel(private val bible: BibleRepository, private val user: UserStore) : ViewModel() {

    private val _home = MutableStateFlow(StoriesHomeState())
    val home: StateFlow<StoriesHomeState> = _home.asStateFlow()

    private val _reader = MutableStateFlow(StoryReaderState())
    val reader: StateFlow<StoryReaderState> = _reader.asStateFlow()

    private var prefs: ReadingPrefs = ReadingPrefs()
    private var anchor: VerseId? = null
    private var saveJob: Job? = null
    private var buildJob: Job? = null

    init {
        viewModelScope.launch {
            val library = withContext(Dispatchers.Default) { StoryLibrary(StoryCatalogue.decode(loadCatalogue())) }
            val finished = user.setting(KEY_FINISHED)?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            val last = user.setting(KEY_LAST)?.let { library[it] }
            _home.value = StoriesHomeState(library, last, finished)
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadCatalogue(): String = Res.readBytes("files/stories.json").decodeToString()

    /** The story to offer while reading [verse] in the Bible ("Part of: ..."). */
    fun storyFor(verse: VerseId): Story? = home.value.library?.storyFor(verse)

    /**
     * Opens a story. With [at], it opens on the card holding that verse, in the account that contains it.
     * Otherwise it resumes where the reader left off, or starts at the introduction when [restart] is set or the
     * story was finished.
     */
    fun open(slug: String, at: VerseId? = null, restart: Boolean = false) {
        val story = home.value.library?.get(slug) ?: return
        buildJob?.cancel()
        _reader.value = StoryReaderState(story = story, loading = true)
        buildJob = viewModelScope.launch {
            val saved = parseChoices(user.setting(KEY_ACCOUNTS + slug))
            anchor = when {
                at != null -> at
                restart || slug in home.value.finished -> null
                else -> user.position(SOURCE_PREFIX + slug)
            }
            val choices = if (at != null) StoryPages.choicesFor(story, at, saved) else saved
            _home.update { it.copy(last = story) }
            user.putSetting(KEY_LAST, slug)
            rebuild(story, choices, target = null)
        }
    }

    /** Called when density or text size changes; regroups the cards and keeps the place. */
    fun setPrefs(newPrefs: ReadingPrefs) {
        val changed = newPrefs.density != prefs.density || newPrefs.fontScale != prefs.fontScale
        prefs = newPrefs
        val s = reader.value.story ?: return
        if (changed && !reader.value.loading) {
            buildJob?.cancel()
            buildJob = viewModelScope.launch { rebuild(s, reader.value.choices, target = null) }
        }
    }

    /** Switches a step to another account (a parallel Gospel) and stays on that step. */
    fun chooseAccount(step: Int, account: Int) {
        val s = reader.value.story ?: return
        if (reader.value.choices[step] == account) return
        val choices = reader.value.choices + (step to account)
        viewModelScope.launch { user.putSetting(KEY_ACCOUNTS + s.slug, encodeChoices(choices)) }
        buildJob?.cancel()
        buildJob = viewModelScope.launch { rebuild(s, choices, target = step) }
    }

    fun onPageSettled(page: Int) {
        val st = reader.value
        val story = st.story ?: return
        if (st.loading || page == st.page) return
        _reader.update { it.copy(page = page) }
        when (val p = st.pages.getOrNull(page)) {
            is StoryPage.Scripture -> {
                anchor = p.first
                saveSoon(story, p.first)
            }
            is StoryPage.End -> markFinished(story)
            else -> Unit
        }
    }

    private fun markFinished(story: Story) {
        if (story.slug in home.value.finished) return
        val finished = home.value.finished + story.slug
        _home.update { it.copy(finished = finished) }
        viewModelScope.launch { user.putSetting(KEY_FINISHED, finished.joinToString(",")) }
    }

    private fun saveSoon(story: Story, verse: VerseId) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            user.savePosition(verse, SOURCE_PREFIX + story.slug)
        }
    }

    private suspend fun rebuild(story: Story, choices: Map<Int, Int>, target: Int?) {
        val pages = StoryPages.build(story, choices, prefs.density, FeedViewModel.grouperFor(prefs)) { bible.units(it) }
        val page = if (target != null) StoryPages.firstPageOfStep(pages, target) else StoryPages.pageOf(pages, anchor)
        (pages.getOrNull(page) as? StoryPage.Scripture)?.let { anchor = it.first }
        _reader.update {
            it.copy(
                story = story,
                pages = pages,
                choices = choices,
                page = page,
                generation = it.generation + 1,
                loading = false,
            )
        }
    }

    private fun parseChoices(s: String?): Map<Int, Int> = s?.split(',')?.mapNotNull { part ->
        val (a, b) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        val step = a.toIntOrNull() ?: return@mapNotNull null
        val account = b.toIntOrNull() ?: return@mapNotNull null
        step to account
    }?.toMap() ?: emptyMap()

    private fun encodeChoices(c: Map<Int, Int>) = c.entries.joinToString(",") { "${it.key}:${it.value}" }

    private companion object {
        const val SOURCE_PREFIX = "story:"
        const val KEY_LAST = "stories.last"
        const val KEY_FINISHED = "stories.finished"
        const val KEY_ACCOUNTS = "stories.accounts."
    }
}
