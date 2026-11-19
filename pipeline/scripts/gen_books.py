#!/usr/bin/env python3
"""Generate shared/core-model/.../KjvBookData.kt (book list + KJV verse counts) from the KJV OSIS source.

Usage: python3 pipeline/scripts/gen_books.py   (run from the repository root)
"""
import re
from collections import defaultdict

SRC = "content/sources/kjv/eng-kjv.osis.xml"
OUT = "shared/core-model/src/commonMain/kotlin/app/bible/core/KjvBookData.kt"

CANON = ("Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov "
         "Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal Matt Mark "
         "Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm Heb Jas 1Pet "
         "2Pet 1John 2John 3John Jude Rev").split()
NAMES = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel",
         "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job",
         "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel",
         "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah",
         "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
         "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
         "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter",
         "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"]


def main():
    s = open(SRC, encoding="utf-8").read()
    counts = defaultdict(dict)
    for osis_id in re.findall(r'<verse osisID="([^"]+)"', s):
        for one in osis_id.split():
            b, c, v = one.split(".")
            if b in CANON:
                counts[b][int(c)] = max(counts[b].get(int(c), 0), int(v))
    total = sum(sum(d.values()) for d in counts.values())
    assert total == 31102, total
    lines = []
    for n, (osis, name) in enumerate(zip(CANON, NAMES), 1):
        d = counts[osis]
        assert sorted(d) == list(range(1, len(d) + 1)), osis
        per_chapter = ",".join(str(d[c]) for c in range(1, len(d) + 1))
        testament = "Testament.OLD" if n <= 39 else "Testament.NEW"
        lines.append(f'        Book({n}, "{osis}", "{name}", {testament}, intArrayOf({per_chapter})),')
    out = ("// GENERATED from content/sources/kjv/eng-kjv.osis.xml by pipeline/scripts/gen_books.py. Do not edit by hand.\n"
           "package app.bible.core\n\n"
           "/** The 66 books of the Protestant canon with KJV versification (verse counts per chapter). */\n"
           "internal object KjvBookData {\n"
           "    val books: List<Book> = listOf(\n" + "\n".join(lines) + "\n    )\n}\n")
    open(OUT, "w", encoding="utf-8").write(out)
    print(f"wrote {OUT}: 66 books, {total} verses")


if __name__ == "__main__":
    main()
