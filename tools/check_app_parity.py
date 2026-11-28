# EH3D phone apps - tools/check_app_parity.py: what the Android app and the iPhone app must hold the same, besides the case files (tools/check_shared_cases.py):
# the examples of the answers of the server, the catalog of the refusal codes, the tables of the 8 languages and the files of the settings page (the copy of the settings contract of the server and the
# table of its sentences in eight languages). The only phrase that may be in the iPhone tables and not in the Android ones is the
# one of the certificate that is pinned (docs/PLATFORM_DIFFERENCES.md says why).
#   python -I tools/check_app_parity.py [--other FOLDER_OF_THE_OTHER_REPOSITORY]
# Without --other it looks for the sibling folder of the other app and, when there is none, asks GitHub (the public repository, main branch; or the branch named by EH3D_PARITY_REF when the other app has it,
# so that the two pull requests of a change that touches both apps are compared with each other).
# Exit code: 0 they agree, 1 they differ or a folder is missing, 2 the other side could not be read (a check that cannot run is a failure).
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

OWNER = "JuanenRac"
APPS = {
    "android": {"repo": "EH3D-ANDROID-CONTROL", "examples": "app/src/test/resources/api-examples", "catalog": "app/src/test/resources", "i18n": "app/src/main/assets/i18n", "assets": "app/src/main/assets"},
    "ios": {"repo": "EH3D-IOS-CONTROL", "examples": "Tests/Eh3dCoreTests/Examples", "catalog": "Tests/Eh3dCoreTests/Catalog", "i18n": "Sources/Eh3dCore/Resources/i18n", "assets": "Sources/Eh3dCore/Resources"},
}
CATALOG_FILE = "error-codes.json"
# the files of the settings page that both apps carry byte for byte as data: the copy of the contract of the server and the sentences of the page in eight languages
ASSET_FILES = ("settings-contract.json", "settings-texts.json")
# the phrases that only the iPhone app has, with the reason (the system makes the difference)
IOS_ONLY = {"El certificado del servidor no es el que se guardó la primera vez: no se conecta.": "the certificate of a secure server is pinned on the iPhone only"}

# the phrases that only the Android app has: the iPhone app has no notices through Apple yet (it needs a key of a paid developer account)
_PUSH = "notices with the app closed are on Android only until there is a key of Apple"
ANDROID_ONLY = {phrase: _PUSH for phrase in (
    "Un trabajo espera al operario.", "Un trabajo ha fallado.", "Un trabajo ha terminado.", "Avisos aunque la app esté cerrada (Firebase)",
    "Apagado por defecto. El servidor envía a través de Google solo qué ha pasado, sin nombres; hay que activarlo también en el servidor.",
)}

_LISTEN = "the listening while a job runs is a foreground service of Android; the iPhone does not let an app go on asking in the background"
ANDROID_ONLY.update({phrase: _LISTEN for phrase in (
    "Escucha del trabajo", "Escuchando mientras hay un trabajo en marcha.", "Avisar al instante mientras hay un trabajo (sin Google)",
    "Apagado por defecto. Mientras haya un trabajo, la app pregunta a su servidor cada pocos segundos aunque no esté a la vista; se apaga sola al terminar.",
)})


def load_folder(folder: Path) -> dict:
    """File name -> the JSON it holds, for the .json files of a folder."""
    return {p.name: json.loads(p.read_bytes().decode("utf-8")) for p in sorted(folder.glob("*.json"))}


def compare_documents(label: str, android: dict, ios: dict) -> list:
    """The problems between two sets of JSON documents that must be equal as data (the layout of the text does not matter)."""
    problems = [f"{label}: {n} is only in the Android app" for n in sorted(set(android) - set(ios))]
    problems += [f"{label}: {n} is only in the iPhone app" for n in sorted(set(ios) - set(android))]
    problems += [f"{label}: {n} differs" for n in sorted(set(android) & set(ios)) if android[n] != ios[n]]
    return problems


def compare_tables(android: dict, ios: dict, ios_only: dict = IOS_ONLY) -> list:
    """The problems between the tables of languages: the same files, every phrase of Android in iOS with the same translation, and in iOS only the phrases that are allowed."""
    problems = [f"i18n: {n} is only in the Android app" for n in sorted(set(android) - set(ios))]
    problems += [f"i18n: {n} is only in the iPhone app" for n in sorted(set(ios) - set(android))]
    for name in sorted(set(android) & set(ios)):
        mine, other = android[name], ios[name]
        if not isinstance(mine, dict) or not isinstance(other, dict):
            problems.append(f"i18n: {name} is not a table of phrases")
            continue
        problems += [f"i18n: {name}: a phrase of the Android app is not in the iPhone app: {k}" for k in sorted(set(mine) - set(other) - set(ANDROID_ONLY))]
        problems += [f"i18n: {name}: a phrase only the iPhone app has and is not allowed: {k}" for k in sorted(set(other) - set(mine) - set(ios_only))]
        problems += [f"i18n: {name}: the translation of a phrase differs: {k}" for k in sorted(set(mine) & set(other)) if mine[k] != other[k]]
    return problems


def compare_all(android: dict, ios: dict) -> list:
    """Every comparison; each side is {"examples": {...}, "catalog": {...}, "i18n": {...}, "assets": {...}}."""
    problems = compare_documents("examples", android["examples"], ios["examples"])
    problems += compare_documents("catalog", android["catalog"], ios["catalog"])
    problems += compare_tables(android["i18n"], ios["i18n"])
    problems += compare_documents("assets", android.get("assets", {}), ios.get("assets", {}))
    return problems


def read_side(root: Path, app: str) -> dict:
    where = APPS[app]
    catalog = load_folder(root / where["catalog"])
    assets = load_folder(root / where["assets"]) if (root / where["assets"]).is_dir() else {}
    return {"examples": load_folder(root / where["examples"]), "catalog": {CATALOG_FILE: catalog[CATALOG_FILE]} if CATALOG_FILE in catalog else {}, "i18n": load_folder(root / where["i18n"]),
            "assets": {name: assets[name] for name in ASSET_FILES if name in assets}}


def _get(url: str) -> bytes:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "eh3d-check-app-parity"}
    if os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]  # the token of the run lifts the limit of anonymous requests
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
        return response.read()


def parity_ref() -> str:
    """The branch of the other app to compare with: the one named by EH3D_PARITY_REF (the branch of a pull request), else main."""
    ref = os.environ.get("EH3D_PARITY_REF", "").strip()
    return ref if ref else "main"


def github_folder(repo: str, rel: str, only=(), ref: str = "") -> dict:
    """The JSON files of a folder of the public repository (the branch `ref`, main by default, and main again when the other app has no such branch); with `only` (a name or a list of names), just those files."""
    wanted = {only} if isinstance(only, str) else set(only)
    ref = ref or parity_ref()
    try:
        listing = json.loads(_get(f"https://api.github.com/repos/{OWNER}/{repo}/contents/{rel}?ref={urllib.parse.quote(ref, safe='')}"))
    except urllib.error.HTTPError as why:
        if why.code != 404 or ref == "main":
            raise
        listing = json.loads(_get(f"https://api.github.com/repos/{OWNER}/{repo}/contents/{rel}?ref=main"))
    out = {}
    for item in listing:
        if item.get("type") == "file" and item["name"].endswith(".json") and (not wanted or item["name"] in wanted):
            out[item["name"]] = json.loads(_get(item["download_url"]).decode("utf-8"))
    return out


def read_github(app: str) -> dict:
    where = APPS[app]
    ref = parity_ref()
    if ref != "main":
        try:
            _get(f"https://api.github.com/repos/{OWNER}/{where['repo']}/branches/{urllib.parse.quote(ref, safe='')}")
        except urllib.error.HTTPError as why:
            if why.code != 404:
                raise
            ref = "main"  # the other app has no branch of that name: its main is what this change must agree with
    return {"examples": github_folder(where["repo"], where["examples"], ref=ref), "catalog": github_folder(where["repo"], where["catalog"], CATALOG_FILE, ref=ref), "i18n": github_folder(where["repo"], where["i18n"], ref=ref),
            "assets": github_folder(where["repo"], where["assets"], ASSET_FILES, ref=ref)}


def which_side(root: Path):
    for key, where in APPS.items():
        if (root / where["i18n"]).is_dir():
            return key
    return None


def main(argv: list) -> int:
    root = Path(__file__).resolve().parent.parent
    side = which_side(root)
    if side is None:
        print("This repository has no tables of languages.")
        return 2
    other_side = "ios" if side == "android" else "android"
    given = argv[argv.index("--other") + 1] if "--other" in argv and argv.index("--other") + 1 < len(argv) else None
    sibling = Path(given) if given else root.parent / APPS[other_side]["repo"]
    try:
        mine = read_side(root, side)
        if (sibling / APPS[other_side]["i18n"]).is_dir():
            other = read_side(sibling, other_side)
        elif given:
            print(f"{given} has no folder {APPS[other_side]['i18n']}.")
            return 2
        else:
            other = read_github(other_side)
    except (OSError, ValueError, urllib.error.URLError) as why:
        print(f"Could not read the other app: {why}")
        return 2
    sides = {side: mine, other_side: other}
    if not sides["android"]["i18n"] or not sides["ios"]["i18n"]:
        print("A folder of tables of languages is empty.")
        return 1
    problems = compare_all(sides["android"], sides["ios"])
    for line in problems:
        print(line)
    if problems:
        print(f"{len(problems)} difference(s) between the Android app and the iPhone app: change both, or write the difference in docs/PLATFORM_DIFFERENCES.md.")
        return 1
    print(f"OK: {len(sides['android']['examples'])} examples, the catalog, {len(sides['android']['i18n'])} tables of languages and {len(sides['android']['assets'])} files of the settings page agree in both apps.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
