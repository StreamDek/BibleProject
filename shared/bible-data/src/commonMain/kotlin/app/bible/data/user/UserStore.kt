package app.bible.data.user

import app.bible.core.VerseId
import app.bible.core.VerseRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The reader's marks on a run of verses. */
data class VerseMarks(
    val highlights: Map<VerseId, Int> = emptyMap(),
    val bookmarks: Set<VerseId> = emptySet(),
    val notes: Map<VerseId, String> = emptyMap(),
) {
    companion object { val None = VerseMarks() }
}

data class SavedNote(val verse: VerseId, val body: String, val updatedAt: Long)

/**
 * Everything the reader creates on this device (user.db): reading position, settings, highlights, bookmarks
 * and notes. It works offline with no account; signed-in sync (Phase 7) builds on the same tables.
 */
@OptIn(ExperimentalTime::class)
class UserStore(
    private val db: UserDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val q get() = db.userQueries

    private val _revision = MutableStateFlow(0)
    /** Bumped after every change to highlights, bookmarks or notes, so open screens reload their marks. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // ---- settings and position ------------------------------------------------------------------------

    suspend fun setting(key: String): String? = withContext(io) { q.settingValue(key).executeAsOneOrNull() }

    suspend fun putSetting(key: String, value: String) = withContext(io) { q.putSetting(key, value) }

    suspend fun position(source: String = FEED): VerseId? = withContext(io) {
        q.position(source).executeAsOneOrNull()?.let { VerseId(it.toInt()) }
    }

    suspend fun savePosition(verse: VerseId, source: String = FEED) = withContext(io) {
        q.putPosition(source, verse.value.toLong(), now())
    }

    // ---- marks ----------------------------------------------------------------------------------------

    suspend fun marks(range: VerseRange): VerseMarks = withContext(io) {
        val a = range.start.value.toLong(); val b = range.end.value.toLong()
        VerseMarks(
            highlights = q.highlightsBetween(a, b).executeAsList().associate { VerseId(it.verse_id.toInt()) to it.color.toInt() },
            bookmarks = q.bookmarksBetween(a, b).executeAsList().mapTo(HashSet()) { VerseId(it.toInt()) },
            notes = q.notesBetween(a, b).executeAsList().associate { VerseId(it.verse_id.toInt()) to it.body },
        )
    }

    /** Sets the highlight colour of [verses], or removes it when [color] is null. */
    suspend fun setHighlight(verses: Collection<VerseId>, color: Int?) = change {
        db.transaction {
            for (v in verses) {
                if (color == null) q.removeHighlight(v.value.toLong())
                else q.putHighlight(v.value.toLong(), color.toLong(), now())
            }
        }
    }

    /** Bookmarks [verse], or removes the bookmark if it already has one. Returns true when now bookmarked. */
    suspend fun toggleBookmark(verse: VerseId): Boolean = change {
        val id = verse.value.toLong()
        val exists = q.bookmarksBetween(id, id).executeAsOneOrNull() != null
        if (exists) q.removeBookmark(id) else q.putBookmark(id, now())
        !exists
    }

    /** Saves the note on [verse]; a blank body deletes it. */
    suspend fun setNote(verse: VerseId, body: String) = change {
        val id = verse.value.toLong()
        val text = body.trim()
        if (text.isEmpty()) q.removeNote(id)
        else db.transaction {
            val t = now()
            q.putNote(id, text, q.noteCreatedAt(id).executeAsOneOrNull() ?: t, t)
        }
    }

    suspend fun bookmarks(): List<VerseId> = withContext(io) {
        q.allBookmarks().executeAsList().map { VerseId(it.verse_id.toInt()) }
    }

    suspend fun notes(): List<SavedNote> = withContext(io) {
        q.allNotes().executeAsList().map { SavedNote(VerseId(it.verse_id.toInt()), it.body, it.updated_at) }
    }

    private suspend fun <T> change(block: () -> T): T = withContext(io) { block() }.also { _revision.update { it + 1 } }

    companion object {
        const val FEED = "feed"
    }
}
