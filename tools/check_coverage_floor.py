# EH3D-ANDROID-CONTROL - tools/check_coverage_floor.py: reads the JaCoCo report of the unit tests and fails when the lines that hold rules are less covered than a floor.
#   python -I tools/check_coverage_floor.py REPORT_OR_FOLDER --floor 70 [--all-floor 20]
# "Rules" are the packages with no screen in them (model, network, update) and the few classes of the root package that are plain logic (the address, the session, the languages, the refusal texts).
# Screens (Compose) cannot be run on the JVM, so they are counted apart ("all") and only checked against their own, lower floor.
# Exit code: 0 the floors hold, 1 a floor does not hold or the report has no lines, 2 the report cannot be found or read.
import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BASE = "es/electrohobby3d/eh3d"
RULE_PACKAGES = (f"{BASE}/model", f"{BASE}/network", f"{BASE}/update")
RULE_CLASSES = (f"{BASE}/ServerEndpoint", f"{BASE}/SessionVault", f"{BASE}/Tr", f"{BASE}/ErrorTexts")


def find_report(path: Path):
    """The JaCoCo XML file: the path itself, or the first XML report under a folder."""
    if path.is_file():
        return path
    if path.is_dir():
        for candidate in sorted(path.rglob("*.xml")):
            try:
                if ET.parse(candidate).getroot().tag == "report":
                    return candidate
            except ET.ParseError:
                continue
    return None


def is_rule_class(name: str) -> bool:
    """True for a class of a rules package or one of the plain-logic classes of the root package (with its inner classes and its file-level class)."""
    if any(name.startswith(package + "/") for package in RULE_PACKAGES):
        return True
    return any(name == base or name.startswith(base + "$") or name == base + "Kt" or name.startswith(base + "Kt$") for base in RULE_CLASSES)


def lines_of(element):
    """(covered, missed) of the LINE counter that is a direct child of an element of the report."""
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            return int(counter.get("covered", "0")), int(counter.get("missed", "0"))
    return 0, 0


def measure(root):
    """((covered, total) of the rules, (covered, total) of everything), counting every class once."""
    rules_covered = rules_missed = all_covered = all_missed = 0
    for package in root.findall("package"):
        for cls in package.findall("class"):
            covered, missed = lines_of(cls)
            all_covered += covered
            all_missed += missed
            if is_rule_class(cls.get("name", "")):
                rules_covered += covered
                rules_missed += missed
    return (rules_covered, rules_covered + rules_missed), (all_covered, all_covered + all_missed)


def percent(pair) -> float:
    covered, total = pair
    return 100.0 * covered / total if total else 0.0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Fails when the lines that hold rules are less covered than a floor.")
    parser.add_argument("report", type=Path, help="the JaCoCo XML report, or a folder that holds it")
    parser.add_argument("--floor", type=float, default=0.0, help="least percent of lines covered in the rules (default 0: only print)")
    parser.add_argument("--all-floor", type=float, default=0.0, help="least percent of lines covered in the whole app (default 0: only print)")
    args = parser.parse_args(argv)
    found = find_report(args.report)
    if found is None:
        print(f"COVERAGE=ERROR no JaCoCo report under {args.report}", file=sys.stderr)
        return 2
    try:
        rules, everything = measure(ET.parse(found).getroot())
    except (ET.ParseError, ValueError) as error:
        print(f"COVERAGE=ERROR {found} cannot be read: {error}", file=sys.stderr)
        return 2
    if rules[1] == 0:
        print("COVERAGE=FAIL the report has no lines in the rules (the packages or the classes were renamed?)", file=sys.stderr)
        return 1
    print(f"COVERAGE rules {percent(rules):.1f} % ({rules[0]}/{rules[1]} lines), whole app {percent(everything):.1f} % ({everything[0]}/{everything[1]} lines)")
    problems = []
    if percent(rules) < args.floor:
        problems.append(f"the rules are covered {percent(rules):.1f} %, below the floor of {args.floor:g} %")
    if percent(everything) < args.all_floor:
        problems.append(f"the whole app is covered {percent(everything):.1f} %, below the floor of {args.all_floor:g} %")
    for line in problems:
        print(f"COVERAGE=FAIL {line}", file=sys.stderr)
    if problems:
        return 1
    print("COVERAGE=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
