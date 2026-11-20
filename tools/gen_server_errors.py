#!/usr/bin/env python3
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
"""Writes app/src/main/assets/server-errors.json: the text of every refusal code of EH3D-SERVER in eight languages.

The codes come from EH3D-SERVER/docs/error-codes.json. The eight texts of a code (English, Spanish, Italian, German, French, European Portuguese, Chinese, Japanese, in that order) come from the row
`err_<code>` of EH3D-STUDIO/src/texts.ts, so Studio and the app say the same words; a code that Studio does not have yet is taken from tools/server_errors_extra.json. A code with no eight texts in either
is an error: no text is ever invented here.

    py -3 -I tools/gen_server_errors.py          write the table
    py -3 -I tools/gen_server_errors.py --check  fail when the table on disk is not the one the sources give (it is skipped when the server or Studio is not next to this repository)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GITHUB = ROOT.parent
CODES = GITHUB / "EH3D-SERVER" / "docs" / "error-codes.json"
TEXTS = GITHUB / "EH3D-STUDIO" / "src" / "texts.ts"
EXTRA = ROOT / "tools" / "server_errors_extra.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "server-errors.json"
ROW = re.compile(r"^\s*err_([a-z0-9_]+):\s*\[(.*)\],?\s*$")
STR = re.compile(r'"((?:[^"\\]|\\.)*)"')


def studio_rows() -> dict:
    rows = {}
    for line in TEXTS.read_text(encoding="utf-8").splitlines():
        match = ROW.match(line)
        if match:
            rows[match.group(1)] = [json.loads('"' + v + '"') for v in STR.findall(match.group(2))]
    return rows


def build() -> str:
    codes = sorted(item["code"] for item in json.loads(CODES.read_text(encoding="utf-8")))
    rows = studio_rows()
    extra = {k: v for k, v in json.loads(EXTRA.read_text(encoding="utf-8")).items() if not k.startswith("_")}
    table, missing = {}, []
    for code in codes:
        texts = rows.get(code) or extra.get(code)
        if not texts or len(texts) != 8 or any(not t.strip() for t in texts):
            missing.append(code)
            continue
        table[code] = texts
    if missing:
        raise SystemExit("no eight texts for: " + ", ".join(missing))
    stale = sorted(set(extra) - set(codes))
    if stale:
        raise SystemExit("tools/server_errors_extra.json has codes the server no longer has: " + ", ".join(stale))
    return json.dumps(table, ensure_ascii=False, indent=0, separators=(",", ":")).replace("\n", "\n") + "\n"


def main() -> int:
    if not CODES.is_file() or not TEXTS.is_file():
        print("SERVER_ERRORS=SKIP (EH3D-SERVER or EH3D-STUDIO is not next to this repository)")
        return 0
    text = build()
    if "--check" in sys.argv:
        current = OUT.read_bytes().decode("utf-8").replace("\r\n", "\n") if OUT.is_file() else ""
        if current != text:
            print("SERVER_ERRORS=FAIL (app/src/main/assets/server-errors.json is not the one the sources give: run py -3 -I tools/gen_server_errors.py)")
            return 1
        print("SERVER_ERRORS=PASS")
        return 0
    OUT.write_bytes(text.encode("utf-8"))
    print(f"wrote {OUT.relative_to(ROOT)} ({len(json.loads(text))} codes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
