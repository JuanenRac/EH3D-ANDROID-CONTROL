#!/usr/bin/env python3
# =============================================================================
# EH3D-ANDROID-CONTROL - tools/check_languages.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Checks that the Android app has the eight languages and no other.

Before (any machine):   python tools/check_languages.py
    the build keeps exactly the resources of the eight languages (androidResources.localeFilters of app/build.gradle.kts), nothing uses the older resConfigs setting, and no resource
    folder (values-xx and the like) is of another language.
After (the APK is built):   python tools/check_languages.py badging.txt
    the "locales:" line of `aapt2 dump badging` of the APK names no other language.

Prints LANGUAGES=PASS or FAIL with the reasons.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILTERS = ["en", "es", "de", "fr", "it", "ja", "zh-rCN", "pt-rPT"]
LANGUAGES = ["en", "es", "de", "fr", "it", "ja", "zh", "pt"]
# the only regions of the two languages whose texts depend on the region: Chinese of the mainland and European Portuguese
REGIONS = {"zh": "CN", "pt": "PT"}
NOT_A_LANGUAGE = {"car"}  # a qualifier of two or three letters that is not a language


def folder_locale(name: str):
    """(language, region or None) of a resource folder such as values-pt-rBR or values-b+zh+Hans, or None for a folder of no language."""
    parts = name.split("-")[1:]
    while parts and re.fullmatch(r"m[cn]c\d+", parts[0]):  # the mobile country and network codes come before the language
        parts = parts[1:]
    if not parts:
        return None
    if parts[0].startswith("b+"):
        tags = parts[0].split("+")[1:]
        return tags[0], next((t for t in tags[1:] if len(t) == 2 or t.isdigit()), None)
    if not re.fullmatch(r"[a-z]{2,3}", parts[0]) or parts[0] in NOT_A_LANGUAGE:
        return None
    region = parts[1][1:] if len(parts) > 1 and re.fullmatch(r"r[A-Z]{2}", parts[1]) else None
    return parts[0], region


def allowed(language: str, region) -> bool:
    if language not in LANGUAGES:
        return False
    return region is None or language not in REGIONS or region == REGIONS[language]


def source_problems(root: Path) -> list[str]:
    problems = []
    text = (root / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    code = "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("//"))
    declared = re.findall(r"localeFilters\s*\+?=\s*(?:listOf|setOf)\(([^)]*)\)", code)
    if len(declared) != 1 or code.count("localeFilters") != 1:
        problems.append("app/build.gradle.kts must set androidResources.localeFilters once, as one list")
    else:
        listed = re.findall(r'"([^"]*)"', declared[0])
        if sorted(listed) != sorted(FILTERS):
            extra = sorted(set(listed) - set(FILTERS))
            missing = sorted(set(FILTERS) - set(listed))
            problems.append(f"localeFilters of app/build.gradle.kts must be {', '.join(FILTERS)} (extra: {', '.join(extra) or 'none'}; missing: {', '.join(missing) or 'none'})")
    if re.search(r"\b(resConfigs?|resourceConfigurations)\b", code):
        problems.append("app/build.gradle.kts uses resConfigs or resourceConfigurations: the languages are said in localeFilters only")
    for folder in sorted(root.glob("app/src/*/res/*")):
        locale = folder_locale(folder.name) if folder.is_dir() else None
        if locale and not allowed(*locale):
            problems.append(f"{folder.relative_to(root).as_posix()} is a resource folder of a language that is not among the eight")
    return problems


def apk_problems(badging: str) -> list[str]:
    line = next((x for x in badging.splitlines() if x.startswith("locales:")), None)
    if line is None:
        return ["the report of the APK has no locales line: it must be the output of aapt2 dump badging"]
    found = [x for x in re.findall(r"'([^']*)'", line) if x != "--_--"]  # --_-- is the default texts
    problems = []
    languages = set()
    for tag in found:
        parts = re.split(r"[-_]", tag)
        language = parts[0]
        region = next((p for p in parts[1:] if len(p) == 2 or p.isdigit()), None)
        languages.add(language)
        if not allowed(language, region):
            problems.append(f"the APK carries texts for {tag}, which is not among the eight languages")
    missing = [x for x in LANGUAGES if x != "en" and x not in languages]  # English is the default
    if missing:
        problems.append(f"the APK carries no texts for: {', '.join(missing)}")
    return problems


def main(argv: list[str]) -> int:
    problems = source_problems(ROOT)
    if len(argv) > 1:
        badging = Path(argv[1]).read_text(encoding="utf-8", errors="replace")
        print(next((x for x in badging.splitlines() if x.startswith("locales:")), "locales: (none)"))
        problems += apk_problems(badging)
    for problem in problems:
        print("  FAIL", problem)
    print(f"LANGUAGES={'FAIL' if problems else 'PASS'}")
    return 1 if problems else 0


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)
    sys.exit(main(sys.argv))
