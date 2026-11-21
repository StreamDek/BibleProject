package app.bible.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.data.VerseText
import app.bible.app.ui.LocalScriptureFont
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.study.BibleEntity
import app.bible.data.study.CrossReference
import app.bible.data.study.EntityNameForm
import app.bible.data.study.Language
import app.bible.data.study.StepBooks
import kotlinx.coroutines.launch

private val RELATIONS = listOf(
    "parent" to "Parents", "partner" to "Married to", "sibling" to "Brothers and sisters", "child" to "Children",
    "founder" to "Founded by", "resident" to "People who lived there",
)

/** "Abraham@Gen.11.26-1Pe" → "Genesis to 1 Peter"; tells namesakes apart. */
fun entitySpan(e: BibleEntity): String {
    val after = e.uniqueName.substringAfter('@', "")
    fun name(code: String) = StepBooks.number(code)?.let { Canon.book(it).name }
    val first = name(after.substringBefore('.'))
    val last = after.substringAfter('-', "").takeIf { it.isNotEmpty() }?.let { name(it) }
    return when {
        first == null -> ""
        last == null || last == first -> first
        else -> "$first to $last"
    }
}

/**
 * A person, place or other name (STEP Bible TIPNR): what the data says about them, their family or the place's
 * people, every name form in Hebrew and Greek, the stories they appear in and every verse that names them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityPage(vm: StudyViewModel, id: Int, onEntity: (Int) -> Unit, onName: (String) -> Unit, onOpenVerse: (VerseId) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val stories = LocalStudyStories.current
    val scope = rememberCoroutineScope()
    var entity by remember(id) { mutableStateOf<BibleEntity?>(null) }
    var names by remember(id) { mutableStateOf<List<EntityNameForm>>(emptyList()) }
    var links by remember(id) { mutableStateOf<Map<String, List<BibleEntity>>>(emptyMap()) }
    var storySlugs by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    val refs = remember(id) { mutableStateListOf<Pair<VerseId, VerseText?>>() }
    var more by remember(id) { mutableStateOf(true) }

    suspend fun loadMore() {
        val s = vm.store()
        val page = s.entityRefs(id, refs.lastOrNull()?.first, 20)
        val texts = vm.verses(page)
        refs.addAll(page.map { it to texts[it] })
        more = page.size == 20
    }
    LaunchedEffect(id) {
        val s = vm.store()
        entity = s.entity(id)
        names = s.entityNames(id)
        links = s.entityLinks(id)
        storySlugs = s.entityStories(id)
        loadMore()
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
        val e = entity
        if (e == null) { SkeletonLine(0.5f, 36); SkeletonLine(0.8f); SkeletonLine(0.7f); return@Column }
        Text(
            when (e.kind) { "person" -> "PERSON"; "place" -> "PLACE"; else -> e.type.uppercase() },
            style = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold), color = colors.primary,
        )
        Text(e.name, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 34.sp))
        Text(
            listOfNotNull(e.type.takeIf { e.kind != "other" }, entitySpan(e).takeIf { it.isNotEmpty() }, "${e.verses} verses").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
        e.description?.let { Spacer(Modifier.height(10.dp)); Text(it, style = MaterialTheme.typography.bodyLarge) }
        e.tribe?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.secondary) }
        e.area?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.secondary) }
        if (e.lat != null && e.lon != null) {
            TextButton(onClick = { runCatching { uri.openUri("https://www.google.com/maps/search/?api=1&query=${e.lat},${e.lon}") } }) {
                Text("Show on a map")
            }
        }

        e.summary?.let {
            StudyCard {
                for (line in it.lines()) Text(line.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        for ((rel, title) in RELATIONS) {
            val list = links[rel] ?: continue
            StudyHeading(title)
            FlowRow(horizontalArrangement = ChipSpacing, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in list) EntityChip(o) { onEntity(o.id) }
            }
        }

        if (names.isNotEmpty()) {
            StudyHeading("Names in Hebrew and Greek")
            for (n in names) {
                val greek = n.stepId.startsWith("G")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onName(n.stepId) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(n.original, style = originalStyle(if (greek) Language.Greek else Language.Hebrew, 22.sp).copy(textDirection = TextDirection.Content))
                        Text("${n.translated} · ${n.significance}", style = MaterialTheme.typography.bodySmall, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(n.stepId, style = MaterialTheme.typography.labelLarge, color = StudyColors.step())
                }
            }
        }

        if (stories != null) {
            val titled = storySlugs.mapNotNull { slug -> stories.title(slug)?.let { slug to it } }
            if (titled.isNotEmpty()) {
                StudyHeading("In VerseUp stories")
                FlowRow(horizontalArrangement = ChipSpacing, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((slug, title) in titled.take(24)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)).clickable { stories.open(slug) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        StudyHeading("Every verse that names ${if (e.kind == "place") "it" else if (e.type == "Female") "her" else if (e.type == "Male") "him" else "them"}")
        for ((v, t) in refs) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenVerse(v) }.padding(vertical = 8.dp)) {
                Text(v.display(), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                t?.let { Text(it.text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (more) TextButton(onClick = { scope.launch { loadMore() } }) { Text("More verses") }

        Spacer(Modifier.height(20.dp))
        Text(
            "From STEP Bible's TIPNR (STEPBible.org, Tyndale House), CC BY 4.0. Only its data is shown; its longer AI-assisted descriptions are not used.",
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
    }
}

@Composable
fun EntityChip(e: BibleEntity, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.name, style = MaterialTheme.typography.bodyMedium)
        val span = entitySpan(e)
        if (span.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(span, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** People and places a word names, shown on its word study. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamedEntities(entities: List<BibleEntity>, onEntity: (Int) -> Unit) {
    if (entities.isEmpty()) return
    StudyHeading(
        when {
            entities.all { it.kind == "person" } -> if (entities.size == 1) "Person" else "People with this name"
            entities.all { it.kind == "place" } -> if (entities.size == 1) "Place" else "Places with this name"
            else -> "Who or what this names"
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in entities.take(8)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
                    .clickable { onEntity(e.id) }.padding(14.dp),
            ) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(e.description ?: e.area, entitySpan(e).takeIf { it.isNotEmpty() }, "${e.verses} verses").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Cross-references from one verse (OpenBible.info), most voted first, each with its text. */
@Composable
fun CrossRefsPage(vm: StudyViewModel, verse: VerseId, onOpenVerse: (VerseId) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var refs by remember(verse) { mutableStateOf<List<CrossReference>?>(null) }
    var texts by remember(verse) { mutableStateOf<Map<VerseId, VerseText>>(emptyMap()) }
    var shown by remember(verse) { mutableStateOf(25) }
    var source by remember(verse) { mutableStateOf<VerseText?>(null) }
    LaunchedEffect(verse) {
        source = vm.verse(verse)
        val list = runCatching { vm.store().crossRefs(verse, 200) }.getOrDefault(emptyList())
        refs = list
        texts = vm.verses(list.map { it.start })
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
        Text("${verse.display().uppercase()} · CROSS-REFERENCES", style = TextStyle(fontSize = 11.sp, letterSpacing = 1.4.sp), color = colors.primary)
        source?.let { Text(it.text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), color = colors.onSurface.copy(alpha = 0.8f)) }
        Spacer(Modifier.height(12.dp))
        val list = refs
        when {
            list == null -> { SkeletonLine(0.3f); SkeletonLine(0.9f); SkeletonLine(0.8f) }
            list.isEmpty() -> Text("No cross-references for this verse.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            else -> {
                Text("${list.size} related passages, most agreed on first", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                for (r in list.take(shown)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenVerse(r.start) }.padding(vertical = 9.dp)) {
                        Text(if (r.end == r.start) r.start.display() else VerseRange(r.start, r.end).display(), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        texts[r.start]?.let { Text(it.text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (list.size > shown) TextButton(onClick = { shown += 50 }) { Text("Show more") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Cross-references from OpenBible.info, CC BY, ranked by readers' votes.", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
    }
}
