#!/usr/bin/env python3
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
"""Checks the tables of the 37 languages of the app without a build: the list of languages, one JSON table per language, the same phrases in every complete table,
the same holes (%s, %d, %.1f) as the Spanish phrase that is the key, no blank text and no table that is only English.

The list is read from the source (Tr.kt here, Tr.swift in the iPhone app), so a language added there without its table, or a table without its language, fails.
Run it with `python -I tools/i18n_check.py`; the unit tests of the app do the same checks inside the build.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIRST_EIGHT = ["en", "es", "it", "de", "fr", "pt", "zh", "ja"]
VARIANTS = {"pt-br": "pt", "fr-ca": "fr"}  # a variant holds only what differs from its main language
MAX_ENGLISH = 30  # a table with more texts equal to the English ones is not translated
HOLE = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")

ANDROID = (ROOT / "app/src/main/java/es/electrohobby3d/eh3d/Tr.kt", ROOT / "app/src/main/assets/i18n")
IOS = (ROOT / "Sources/Eh3dCore/Tr.swift", ROOT / "Sources/Eh3dCore/Resources/i18n")


def holes(text: str) -> list[str]:
    return sorted(re.sub(r"\d+\$", "", h) for h in HOLE.findall(text.replace("%%", "")))


def listed_codes(source: Path) -> list[str]:
    text = source.read_text(encoding="utf-8")
    if source.suffix == ".kt":
        start = text.index("val languages")
        block = text[start:text.index("\n    )", start)]
        return re.findall(r'"([a-z]{2}(?:-[a-z]{2})?)" to "', block)
    start = text.index("static let languages")
    block = text[start:text.index("\n    ]", start)]
    return re.findall(r'\("([a-z]{2}(?:-[a-z]{2})?)", "', block)


def problems_of(source: Path, tables: Path) -> list[str]:
    problems: list[str] = []
    codes = listed_codes(source)
    if len(codes) != 37 or len(set(codes)) != 37:
        problems.append(f"{source.name} lists {len(codes)} languages ({len(set(codes))} different), it must list 37")
    if codes[:8] != FIRST_EIGHT:
        problems.append(f"the first eight languages are {codes[:8]}, they must be {FIRST_EIGHT}")
    files = {p.stem: p for p in tables.glob("*.json")}
    for code in codes:
        if code != "es" and code not in files:
            problems.append(f"{code} is in the list and has no table {tables.name}/{code}.json")
    for stem in files:
        if stem not in codes:
            problems.append(f"{stem}.json is in {tables.name} and its language is not in the list")
    if "en" not in files:
        return problems + ["the English table is missing"]
    loaded: dict[str, dict[str, str]] = {}
    for stem, path in files.items():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except ValueError as exc:
            problems.append(f"{path.name} is not valid JSON: {exc}")
            continue
        if not isinstance(data, dict) or not all(isinstance(v, str) for v in data.values()):
            problems.append(f"{path.name} is not an object of texts")
            continue
        loaded[stem] = data
    english = loaded.get("en", {})
    for stem, data in sorted(loaded.items()):
        blank = [k for k, v in data.items() if not v.strip()]
        if blank:
            problems.append(f"{stem}.json has {len(blank)} blank texts, the first for: {blank[0][:60]}")
        wrong = [k for k, v in data.items() if holes(k) != holes(v)]
        if wrong:
            problems.append(f"{stem}.json changes the holes of {len(wrong)} texts, the first: {wrong[0][:60]}")
        if stem in VARIANTS:
            extra = [k for k in data if k not in english]
            if extra:
                problems.append(f"{stem}.json has {len(extra)} phrases that the English table does not have, the first: {extra[0][:60]}")
            continue
        if set(data) != set(english):
            problems.append(f"{stem}.json does not have the phrases of en.json (missing {len(set(english) - set(data))}, extra {len(set(data) - set(english))})")
        elif stem != "en":
            same = sum(1 for k in english if data[k] == english[k])
            if same > MAX_ENGLISH:
                problems.append(f"{stem}.json keeps {same} texts equal to the English ones")
    return problems


def main() -> int:
    source, tables = ANDROID if ANDROID[0].is_file() else IOS
    problems = problems_of(source, tables)
    for problem in problems:
        print("  FAIL", problem)
    if problems:
        print("I18N_CHECK=FAIL")
        return 1
    print(f"I18N_CHECK=PASS languages=37 tables={len(list(tables.glob('*.json')))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
