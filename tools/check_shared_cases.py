# EH3D phone apps - tools/check_shared_cases.py: the case files that the Android app and the iPhone app both test from must be the same bytes.
#   python -I tools/check_shared_cases.py [--other FOLDER_OF_THE_OTHER_REPOSITORY]
# Without --other it looks for the sibling folder of the other app and, when there is none, asks GitHub (the public repository, main branch).
# Exit code: 0 the files are the same, 1 they differ or one is missing, 2 the other side could not be read (a check that cannot run is a failure).
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

OWNER = "JuanenRac"
SIDES = {
    "android": ("EH3D-ANDROID-CONTROL", "app/src/test/resources/shared-cases"),
    "ios": ("EH3D-IOS-CONTROL", "Tests/Eh3dCoreTests/Cases"),
}


def digests(folder: Path) -> dict:
    """File name -> sha-256 of the case files in a folder (the lines of text are compared as they are in the repository)."""
    return {p.name: hashlib.sha256(p.read_bytes().replace(b"\r\n", b"\n")).hexdigest() for p in sorted(folder.glob("*.json"))}


def compare(mine: dict, other: dict) -> list:
    """The problems found between two sets of digests, in words; empty when they are the same."""
    problems = [f"{n}: only in this repository" for n in sorted(set(mine) - set(other))]
    problems += [f"{n}: only in the other repository" for n in sorted(set(other) - set(mine))]
    problems += [f"{n}: differs" for n in sorted(set(mine) & set(other)) if mine[n] != other[n]]
    return problems


def which_side(root: Path):
    for key, (repo, rel) in SIDES.items():
        if (root / rel).is_dir():
            return key
    return None


def from_github(repo: str, rel: str) -> dict:
    """Digest of each case file of the other repository, read from the raw files of its main branch."""
    base = f"https://api.github.com/repos/{OWNER}/{repo}/contents/{rel}?ref=main"
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "eh3d-check-shared-cases"}
    if os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]  # the token of the run lifts the limit of anonymous requests
    request = urllib.request.Request(base, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        listing = json.load(response)
    out = {}
    for item in listing:
        if item.get("type") == "file" and item["name"].endswith(".json"):
            raw = urllib.request.Request(item["download_url"], headers={"User-Agent": "eh3d-check-shared-cases"})
            with urllib.request.urlopen(raw, timeout=30) as response:
                out[item["name"]] = hashlib.sha256(response.read().replace(b"\r\n", b"\n")).hexdigest()
    return out


def main(argv: list) -> int:
    root = Path(__file__).resolve().parent.parent
    side = which_side(root)
    if side is None:
        print("This repository has no folder of shared case files.")
        return 2
    other_key = "ios" if side == "android" else "android"
    other_repo, other_rel = SIDES[other_key]
    mine = digests(root / SIDES[side][1])
    if not mine:
        print("The folder of shared case files is empty.")
        return 1
    given = argv[argv.index("--other") + 1] if "--other" in argv and argv.index("--other") + 1 < len(argv) else None
    sibling = Path(given) if given else root.parent / other_repo
    try:
        if (sibling / other_rel).is_dir():
            other = digests(sibling / other_rel)
        elif given:
            print(f"{given} has no folder {other_rel}.")
            return 2
        else:
            other = from_github(other_repo, other_rel)
    except (OSError, ValueError, urllib.error.URLError) as why:
        print(f"Could not read the case files of {other_repo}: {why}")
        return 2
    problems = compare(mine, other)
    for line in problems:
        print(line)
    if problems:
        print(f"The case files of {SIDES[side][0]} and {other_repo} must be the same: change them in both.")
        return 1
    print(f"OK: {len(mine)} shared case files are the same in both apps.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
