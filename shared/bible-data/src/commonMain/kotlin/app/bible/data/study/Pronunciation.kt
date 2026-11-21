package app.bible.data.study

/**
 * Strong's pronunciation guides ("el-o-heem'", "ag-ah'-pay") are spelled for English readers: hyphens part the
 * syllables and an apostrophe follows the stressed one. The phone's English voice reads the syllables best as
 * separate words, so this turns the guide into exactly that. Nothing is added or guessed: every letter comes from
 * Strong's.
 */
object Pronunciation {
    /** "el-o-heem'" → "el o heem". Null when there is no guide to read. */
    fun speakable(pron: String?): String? {
        val syllables = pron.orEmpty()
            .substringBefore(" or ") // where Strong's gives two forms, the first is the word's own
            .replace(Regex("\\([^)]*\\)"), " ") // optional sounds in brackets are left out
            .split('-', ' ', ',', ';')
            .map { it.filter { c -> c.isLetter() }.lowercase() }
            .filter { it.isNotEmpty() }
        return syllables.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}
