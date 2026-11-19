package app.bible.pipeline.art

/**
 * A self-contained review page (proposal §6.2 step 5). It shows each story's brief beside its candidates; the
 * editor picks one to approve, or rejects or asks for new seeds with a reason, then downloads decisions.tsv and
 * applies it with `./gradlew :pipeline:artApplyReview -Pdecisions=<file>`. Nothing is published without approval.
 */
object ReviewPage {
    fun html(pending: List<Pair<ArtBrief, CandidateSet>>): String = buildString {
        append(
            """
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Story art review</title>
            <style>
            body{font:15px/1.45 system-ui,sans-serif;margin:0;background:#f6f4ef;color:#222}
            header{position:sticky;top:0;background:#222;color:#fff;padding:12px 20px;display:flex;gap:16px;align-items:center;z-index:2}
            header button{font:inherit;padding:6px 14px}
            main{padding:16px 20px;max-width:1300px;margin:auto}
            section{background:#fff;border-radius:12px;padding:16px;margin:0 0 18px;box-shadow:0 1px 3px #0002}
            h2{margin:0 0 4px;font-size:18px} .ref{color:#666} .meta{font-size:13px;color:#555;margin:8px 0}
            details{font-size:13px;color:#444;margin:6px 0} pre{white-space:pre-wrap}
            .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin-top:10px}
            figure{margin:0;border:3px solid transparent;border-radius:10px;overflow:hidden;cursor:pointer;background:#ddd}
            figure img{width:100%;aspect-ratio:9/16;object-fit:cover;display:block}
            figure.on{border-color:#2a7}
            figcaption{font-size:12px;padding:4px 8px}
            .row{display:flex;gap:8px;align-items:center;margin-top:10px;flex-wrap:wrap}
            .row input{flex:1;min-width:200px;font:inherit;padding:6px}
            .done{opacity:.55}
            </style></head><body>
            <header><b>Story art review</b><span id="count"></span><span style="flex:1"></span>
            <button onclick="download()">Download decisions.tsv</button></header><main>
            <p>Every image must be checked against the imagery policy: God and Jesus only as light with no face (or a faceless hand where the brief allows it); people of the ancient Near East and Mediterranean; no text, modern objects, halos, gore or nudity; nothing the passage does not say. Click a candidate to approve it, or reject with a reason.</p>
            """.trimIndent(),
        )
        if (pending.isEmpty()) append("<p><b>Nothing is waiting for review.</b></p>")
        for ((b, set) in pending) {
            val id = esc(b.slug)
            append("<section id=\"s-$id\" data-slug=\"$id\" data-hash=\"${esc(set.briefHash)}\" data-round=\"${set.round}\">")
            append("<h2>${esc(b.title)}</h2><div class=\"ref\">${esc(b.reference)}</div>")
            append("<div class=\"meta\">Depiction: ${b.depiction.label} · God or Jesus: ${when (b.divine) { DivinePresence.LightSilhouette -> "light silhouette only"; DivinePresence.FacelessHand -> "faceless hand only"; DivinePresence.None -> "not shown" }} · ")
            append("People: ${if (b.people) "yes" else "no"} · Model: ${esc(set.model)} · Round ${set.round}</div>")
            append("<div>${esc(b.scene)}</div>")
            append("<details><summary>Prompt</summary><pre>${esc(set.prompt)}</pre></details>")
            append("<div class=\"grid\">")
            for (f in set.files) {
                val src = "candidates/${b.slug}/${set.briefHash}-r${set.round}/${f.name}"
                append("<figure data-c=\"${esc(f.name)}\" onclick=\"pick(this)\"><img loading=\"lazy\" src=\"${esc(src)}\" alt=\"\"><figcaption>${esc(f.name)}</figcaption></figure>")
            }
            append("</div><div class=\"row\"><input placeholder=\"Reason (needed to reject or regenerate)\">")
            append("<button onclick=\"decide(this,'reject')\">Reject: brief needs changes</button>")
            append("<button onclick=\"decide(this,'regenerate')\">Regenerate (new seeds)</button>")
            append("<button onclick=\"clearIt(this)\">Undo</button></div></section>")
        }
        append(
            """
            </main><script>
            const d = {};
            function sec(el){ return el.closest('section'); }
            function show(){ document.getElementById('count').textContent = Object.keys(d).length + ' of ${pending.size} decided'; }
            function pick(fig){
              const s = sec(fig);
              s.querySelectorAll('figure').forEach(f => f.classList.toggle('on', f === fig));
              d[s.dataset.slug] = {s, action:'approve', c:fig.dataset.c, note:s.querySelector('input').value};
              s.classList.add('done'); show();
            }
            function decide(btn, action){
              const s = sec(btn), note = s.querySelector('input').value.trim();
              if (!note) { alert('Please give a reason.'); return; }
              s.querySelectorAll('figure').forEach(f => f.classList.remove('on'));
              d[s.dataset.slug] = {s, action, c:'', note}; s.classList.add('done'); show();
            }
            function clearIt(btn){ const s = sec(btn); delete d[s.dataset.slug]; s.classList.remove('done');
              s.querySelectorAll('figure').forEach(f => f.classList.remove('on')); show(); }
            function download(){
              const now = new Date().toISOString();
              const lines = Object.entries(d).map(([slug, v]) =>
                [slug, v.s.dataset.hash, v.s.dataset.round, v.action, v.c, '', now, (v.note||'').replace(/[\t\n]/g,' ')].join('\t'));
              const blob = new Blob([lines.join('\n') + '\n'], {type:'text/tab-separated-values'});
              const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'decisions.tsv'; a.click();
            }
            show();
            </script></body></html>
            """.trimIndent(),
        )
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
