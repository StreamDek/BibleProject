package app.bible.app.study

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.core.Attribution
import kotlinx.coroutines.launch

/**
 * Settings → About & Legal → Attributions & Licences. Generated entirely from the attribution registry, so a new
 * dataset, font or library is credited by adding it there.
 */
@Composable
fun AttributionsScreen(vm: StudyViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val registry by vm.registry.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { vm.registry() } }
    var query by rememberSaveable { mutableStateOf("") }
    var licence by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()

    // A Surface gives text and icons the theme's content colour; without it they default to black, unreadable in dark mode.
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                "Attributions & Licences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search sources and licences", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } },
            shape = CircleShape, // search fields are pill-shaped: no sharp corners
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val r = registry ?: return@Column
        val q = query.trim().lowercase()
        val matches = r.resources.filter {
            q.isEmpty() || listOf(it.name, it.author, it.description, it.licence, it.copyright).any { f -> q in f.lowercase() }
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            if (q.isEmpty() && r.about.isNotBlank()) item(key = "about") {
                Text(r.about, style = MaterialTheme.typography.bodyMedium, color = colors.secondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            }
            for (cat in r.categories) {
                val list = matches.filter { it.category == cat.id }
                if (list.isEmpty()) continue
                item(key = "c-${cat.id}") {
                    Text(
                        cat.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
                items(list, key = { it.id }) { a ->
                    AttributionCard(a, onLicence = { file ->
                        scope.launch { licence = a.licence to runCatching { vm.licenceText(file) }.getOrDefault("The licence text could not be opened.") }
                    })
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (matches.isEmpty()) item { Text("No sources match “$query”.", color = colors.secondary, modifier = Modifier.padding(16.dp)) }
        }
    }
    }

    licence?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { licence = null },
            title = { Text(title) },
            text = {
                Text(
                    text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { licence = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun AttributionCard(a: Attribution, onLicence: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    var open by rememberSaveable(a.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.onSurface.copy(alpha = 0.045f))
            .clickable(onClickLabel = if (open) "Collapse" else "Expand") { open = !open }
            .animateContentSize().padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${a.author} · ${a.licence}", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.secondary)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text(a.description, style = MaterialTheme.typography.bodyMedium)
            Field("Credit", a.attribution)
            Field("Copyright", a.copyright)
            Field("Version", a.version)
            a.modifications?.let { Field("Changes made by VerseUp", it) }
            Row {
                TextButton(onClick = { runCatching { uri.openUri(a.url) } }) { Text("Source") }
                a.licenceFile?.let { f -> TextButton(onClick = { onLicence(f) }) { Text("Full licence") } }
                if (a.licenceFile == null) a.licenceUrl?.let { u -> TextButton(onClick = { runCatching { uri.openUri(u) } }) { Text("Licence") } }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    Text(value, style = MaterialTheme.typography.bodySmall)
}
