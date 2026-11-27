#!/usr/bin/env python3
# =============================================================================
# EH3D-ANDROID-CONTROL - tools/third_party_notices.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""The third-party notices of the APK, made from the dependency graph of Gradle and checked against it.

    ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath > app/build/runtime-deps.txt
    python3 tools/third_party_notices.py app/build/runtime-deps.txt            write docs/THIRD_PARTY_NOTICES (reads the repositories)
    python3 tools/third_party_notices.py app/build/runtime-deps.txt --check    fail when a library of the graph is not in the file

The licence of each library is the one its POM (or a parent of it) states, and the NOTICE files are copied from the published
artifacts. `--check` needs no network and compares the names of the libraries, not their versions. The build puts the file in
the assets of the APK.
"""
from __future__ import annotations

import argparse
import io
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = "docs/THIRD_PARTY_NOTICES"
MARKER = "Made by tools/third_party_notices.py from the dependency graph of Gradle; do not edit by hand."
REPOSITORIES = ("https://dl.google.com/dl/android/maven2", "https://repo1.maven.org/maven2")
APACHE_TEXT = "https://www.apache.org/licenses/LICENSE-2.0.txt"
# `+--- group:artifact:version`, with `-> version` when another one was chosen; a line ending in (c) is only a constraint
LINE = re.compile(r"^[ |]*[+\\]--- (?P<group>[A-Za-z0-9_.\-]+):(?P<artifact>[A-Za-z0-9_.\-]+)(?::(?P<asked>[^ ]+(?: [^ \->(]+\})?))?(?: -> (?P<chosen>[^ ]+))?(?P<mark> \([c*n]\))?\s*$")
NOTICE_ENTRY = re.compile(r"(^|/)(notice)([-_.][^/]*)?$", re.IGNORECASE)


def graph(text: str) -> list[tuple[str, str, str]]:
    """(group, artifact, version) of every library of the graph. Constraints and platforms (BOM) carry no code and are left out."""
    found: dict[tuple[str, str], str] = {}
    for line in text.splitlines():
        match = LINE.match(line)
        if not match or (match["mark"] or "").strip() == "(c)" or match["artifact"].endswith("-bom"):
            continue
        version = match["chosen"] or match["asked"] or ""
        if not version or version.startswith("{"):
            continue
        found[(match["group"], match["artifact"])] = version
    if not found:
        raise SystemExit("no library was found: is this the output of `gradle :app:dependencies`?")
    return [(group, artifact, found[(group, artifact)]) for group, artifact in sorted(found)]


def fetch(url: str) -> bytes | None:
    try:
        with urllib.request.urlopen(url, timeout=60) as answer:  # noqa: S310 - fixed https addresses
            return answer.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def published(group: str, artifact: str, version: str, suffix: str) -> bytes | None:
    for repository in REPOSITORIES:
        data = fetch(f"{repository}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.{suffix}")
        if data is not None:
            return data
    return None


def local(element: ElementTree.Element, name: str) -> list[ElementTree.Element]:
    return [child for child in element if child.tag.rsplit("}", 1)[-1] == name]


def words(element: ElementTree.Element, name: str) -> str:
    found = local(element, name)
    return " ".join((found[0].text or "").split()) if found else ""


def licence_name(name: str, url: str) -> str:
    both = f"{name} {url}".lower()
    if "apache" in both and "2.0" in both:
        return "Apache-2.0"
    if re.search(r"\bmit\b", both):
        return "MIT"
    return name or url


def pom_facts(group: str, artifact: str, version: str, depth: int = 0) -> tuple[list[str], str]:
    """(licences, who holds them) as the POM says, asking its parents when it says nothing."""
    data = published(group, artifact, version, "pom")
    if data is None:
        raise SystemExit(f"{group}:{artifact}:{version}: no POM found")
    project = ElementTree.fromstring(data)
    licences = [licence_name(words(item, "name"), words(item, "url")) for block in local(project, "licenses") for item in local(block, "license")]
    holders = [words(item, "name") for block in local(project, "developers") for item in local(block, "developer")]
    holders += [words(item, "name") for item in local(project, "organization")]
    parents = local(project, "parent")
    if not licences and parents and depth < 5:
        licences, above = pom_facts(words(parents[0], "groupId"), words(parents[0], "artifactId"), words(parents[0], "version"), depth + 1)
        holders = holders or [above]
    return sorted(set(licences)), ", ".join(dict.fromkeys(h for h in holders if h)) or f"the authors of {group}"


def notice_files(group: str, artifact: str, version: str) -> list[str]:
    texts = []
    for suffix in ("aar", "jar"):
        data = published(group, artifact, version, suffix)
        if data is None:
            continue
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for name in sorted(archive.namelist()):
                if NOTICE_ENTRY.search(name) and not name.endswith(".class"):
                    texts.append(clean(archive.read(name).decode("utf-8", errors="replace")))
    return [text for text in texts if text]


def clean(text: str) -> str:
    return "\n".join(line.rstrip() for line in text.replace("﻿", "").replace("\r\n", "\n").replace("\r", "\n").split("\n")).strip("\n")


# Libraries under other terms, accepted one family at a time and never by licence name alone.
SDK_TERMS = "Android Software Development Kit License"
SDK_GROUPS = ("com.google.android.gms", "com.google.firebase")
SDK_NOTE = ("Proprietary libraries of Google, used unchanged under the terms of https://developer.android.com/studio/terms.html. The licence of this app carries an\n"
            "additional permission to combine it with them (LICENSE-EXCEPTION.md).")
BSD_LIBRARIES = ("androidx.datastore:datastore-preferences-external-protobuf",)
BSD_TEXT = """Copyright 2008 Google Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or
      other materials provided with the distribution.
    * Neither the name of Google Inc. nor the names of its contributors may be used to endorse or promote products derived from this software without specific
      prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE."""


def accepted(group: str, name: str, licences: list[str]) -> str | None:
    """The licence a library is listed under, or None when it must be looked at first."""
    if licences in (["Apache-2.0"], ["MIT"]):
        return licences[0]
    if group in SDK_GROUPS and licences == [SDK_TERMS]:
        return SDK_TERMS
    if name in BSD_LIBRARIES and licences == ["BSD-3-Clause"]:
        return "BSD-3-Clause"
    return None


def render(libraries: list[tuple[str, str, str]]) -> str:
    rows, holders, notices = [], {}, {}
    used: set[str] = set()
    for group, artifact, version in libraries:
        name = f"{group}:{artifact}"
        licences, who = pom_facts(group, artifact, version)
        licence = accepted(group, name, licences)
        if licence is None:
            raise SystemExit(f"{name} {version}: the licence {licences} has no text here; look at it before it is shipped")
        used.add(licence)
        rows.append(f"{name} | {version} | {licence}")
        holders.setdefault((who, licence), []).append(name)
        for text in notice_files(group, artifact, version):
            notices.setdefault(text, []).append(name)
    if "MIT" in used:
        raise SystemExit("a library under MIT: its own copyright line and licence text have to be added to this tool")
    apache = clean((fetch(APACHE_TEXT) or b"").decode("utf-8"))
    if "TERMS AND CONDITIONS FOR USE, REPRODUCTION" not in apache:
        raise SystemExit("the text of the Apache License could not be read")
    lines = ["Third-party notices", "===================", "", MARKER, "",
             "The app is built with the libraries below, from the Maven repositories of Google and Maven Central. Each is used",
             "unchanged under the licence named beside it.", "",
             "Components", "----------", ""]
    lines += rows
    lines += ["", "Copyright", "---------"]
    for (who, licence), names in holders.items():
        lines += ["", f"Copyright {who} ({licence}):"] + [f"  {name}" for name in names]
    if notices:
        lines += ["", "NOTICE files of the libraries", "-----------------------------"]
        for text, names in notices.items():
            lines += ["", f"--- {', '.join(dict.fromkeys(names))} ---", "", text]
    if SDK_TERMS in used:
        lines += ["", SDK_TERMS, "-" * len(SDK_TERMS), "", SDK_NOTE]
    if "BSD-3-Clause" in used:
        lines += ["", "BSD 3-Clause License (protobuf)", "-------------------------------", "", BSD_TEXT]
    lines += ["", "Apache License, Version 2.0", "---------------------------", "", apache]
    return "\n".join(lines) + "\n"


def problems(written: str, libraries: list[tuple[str, str, str]]) -> list[str]:
    if MARKER not in written:
        return ["the file was not made by this tool"]
    section = written.split("\nComponents\n", 1)[-1].split("\nCopyright\n", 1)[0]
    listed = {line.split(" | ")[0].strip() for line in section.splitlines() if line.count(" | ") == 2}
    return [f"not in the notices: {group}:{artifact} {version}" for group, artifact, version in libraries if f"{group}:{artifact}" not in listed]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("graph", type=Path, help="the output of `gradle :app:dependencies --configuration releaseRuntimeClasspath`")
    parser.add_argument("--output", type=Path, default=ROOT / OUTPUT)
    parser.add_argument("--check", action="store_true", help="write nothing; fail when the file does not cover the graph")
    args = parser.parse_args(argv)
    libraries = graph(args.graph.read_text(encoding="utf-8", errors="replace"))
    if args.check:
        found = problems(args.output.read_text(encoding="utf-8"), libraries) if args.output.is_file() else [f"{args.output.name} is missing"]
        for problem in found:
            print(f"- {problem}")
        print(f"THIRD_PARTY_NOTICES={'FAIL' if found else 'PASS'} ({len(libraries)} libraries)")
        if found:
            print("Make the file again with tools/third_party_notices.py (same argument without --check) and commit it.")
        return 1 if found else 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(render(libraries).encode("utf-8"))
    print(f"wrote {args.output} ({len(libraries)} libraries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
