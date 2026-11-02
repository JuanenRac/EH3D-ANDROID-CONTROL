#!/usr/bin/env python3
# =============================================================================
# EH3D-ANDROID-CONTROL - tools/check_release_build.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Checks the report of the shrinker of the release build (app/build/outputs/mapping/release/usage.txt, which lists what R8 removed).

The release is shrunk, the debug build is not, so a class that a library makes by reflection can be fine in every test and gone in the app that is installed. The classes that the
library of the jobs (Room) generates are made through their empty constructor: if the shrinker removed that constructor the app closes at once when it starts. This fails when it did.

    python tools/check_release_build.py [usage.txt]
"""
import re
import sys
from pathlib import Path

DEFAULT = Path(__file__).resolve().parents[1] / "app" / "build" / "outputs" / "mapping" / "release" / "usage.txt"


def removed_constructors(text: str) -> list[str]:
    """The generated classes (`*_Impl`) whose empty constructor appears among the removed members."""
    bad: list[str] = []
    current = None
    for line in text.splitlines():
        if line and not line.startswith(" "):
            current = line[:-1] if line.endswith(":") else None
        elif current and current.endswith("_Impl") and re.fullmatch(r"\s+(public |protected |private )?void <init>\(\)", line):
            bad.append(current)
    return bad


def main(argv: list[str]) -> int:
    path = Path(argv[1]) if len(argv) > 1 else DEFAULT
    if not path.is_file():
        print(f"RELEASE_BUILD=FAIL there is no report of the shrinker at {path}: build the release first")
        return 1
    bad = removed_constructors(path.read_text(encoding="utf-8", errors="replace"))
    for name in bad:
        print(f"  the empty constructor of {name} was removed: it is made by reflection and the app would close when it starts")
    print(f"RELEASE_BUILD={'FAIL' if bad else 'PASS'} generated_classes_without_constructor={len(bad)}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
