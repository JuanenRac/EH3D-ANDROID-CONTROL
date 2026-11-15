# EH3D-ANDROID-CONTROL - tools/test_check_coverage_floor.py: the reading of the JaCoCo report and the floors, with small reports made for the test.   python -I tools/test_check_coverage_floor.py
import contextlib
import importlib.util
import io
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("check_coverage_floor", Path(__file__).resolve().parent / "check_coverage_floor.py")
C = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(C)

BASE = "es/electrohobby3d/eh3d"


def report(classes):
    """A JaCoCo report with one package per prefix; `classes` is a list of (name, covered, missed)."""
    packages = {}
    for name, covered, missed in classes:
        packages.setdefault(name.rsplit("/", 1)[0], []).append((name, covered, missed))
    parts = ['<?xml version="1.0" encoding="UTF-8"?><report name="app">']
    for package, items in packages.items():
        parts.append(f'<package name="{package}">')
        for name, covered, missed in items:
            parts.append(f'<class name="{name}"><method name="m" desc="()V" line="1"><counter type="LINE" missed="99" covered="99"/></method><counter type="LINE" missed="{missed}" covered="{covered}"/></class>')
        parts.append("</package>")
    parts.append("</report>")
    return "".join(parts)


class Selection(unittest.TestCase):
    def test_the_packages_without_screens_are_rules(self):
        for name in (f"{BASE}/model/Models", f"{BASE}/model/FleetMachine", f"{BASE}/network/Eh3dApi", f"{BASE}/network/Roles", f"{BASE}/update/SemanticVersion"):
            with self.subTest(name=name):
                self.assertTrue(C.is_rule_class(name))

    def test_the_plain_logic_classes_of_the_root_package_are_rules_with_their_inner_classes(self):
        for name in (f"{BASE}/ServerEndpoint", f"{BASE}/ServerEndpoint$Companion", f"{BASE}/SessionVault", f"{BASE}/SessionVault$Companion", f"{BASE}/Tr", f"{BASE}/TrKt", f"{BASE}/ErrorTexts"):
            with self.subTest(name=name):
                self.assertTrue(C.is_rule_class(name))

    def test_the_screens_and_the_android_classes_are_not_rules(self):
        for name in (f"{BASE}/HomeScreensKt", f"{BASE}/Eh3dViewModel", f"{BASE}/Eh3dActivity", f"{BASE}/MjpegFeed", f"{BASE}/KeystoreBox", f"{BASE}/Eh3dTheme", f"{BASE}/Trailer", f"{BASE}/ServerEndpointer"):
            with self.subTest(name=name):
                self.assertFalse(C.is_rule_class(name))

    def test_a_package_with_a_similar_name_is_not_a_rules_package(self):
        self.assertFalse(C.is_rule_class(f"{BASE}/modelling/Thing"))
        self.assertFalse(C.is_rule_class(f"{BASE}/network"))


class Measuring(unittest.TestCase):
    def measure(self, classes):
        return C.measure(ET.fromstring(report(classes)))

    def test_the_lines_of_the_class_count_and_the_lines_of_its_methods_do_not_count_twice(self):
        rules, everything = self.measure([(f"{BASE}/model/Models", 8, 2)])
        self.assertEqual(rules, (8, 10))
        self.assertEqual(everything, (8, 10))

    def test_the_rules_and_the_whole_app_are_counted_apart(self):
        rules, everything = self.measure([(f"{BASE}/model/Models", 8, 2), (f"{BASE}/HomeScreensKt", 0, 90)])
        self.assertEqual(rules, (8, 10))
        self.assertEqual(everything, (8, 100))

    def test_a_class_without_a_line_counter_counts_as_nothing(self):
        root = ET.fromstring('<report><package name="p"><class name="es/electrohobby3d/eh3d/model/X"/></package></report>')
        self.assertEqual(C.measure(root), ((0, 0), (0, 0)))

    def test_the_percent_of_nothing_is_zero(self):
        self.assertEqual(C.percent((0, 0)), 0.0)
        self.assertEqual(C.percent((1, 4)), 25.0)
        self.assertEqual(C.percent((3, 3)), 100.0)

    def test_only_the_line_counter_of_the_class_is_read(self):
        element = ET.fromstring('<class><counter type="BRANCH" missed="5" covered="5"/><counter type="LINE" missed="1" covered="3"/></class>')
        self.assertEqual(C.lines_of(element), (3, 1))
        self.assertEqual(C.lines_of(ET.fromstring("<class/>")), (0, 0))


class Command(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def run_tool(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = C.main(argv)
        return code, out.getvalue(), err.getvalue()

    def write(self, name, classes):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(report(classes), encoding="utf-8")
        return path

    def test_a_report_above_the_floor_passes_and_prints_both_numbers(self):
        path = self.write("report.xml", [(f"{BASE}/model/Models", 8, 2), (f"{BASE}/HomeScreensKt", 10, 90)])
        code, out, err = self.run_tool([str(path), "--floor", "70"])
        self.assertEqual(code, 0)
        self.assertIn("rules 80.0 % (8/10 lines), whole app 16.4 % (18/110 lines)", out)
        self.assertIn("COVERAGE=PASS", out)
        self.assertEqual(err, "")

    def test_a_report_below_the_floor_of_the_rules_fails(self):
        path = self.write("report.xml", [(f"{BASE}/model/Models", 5, 5)])
        code, _, err = self.run_tool([str(path), "--floor", "70"])
        self.assertEqual(code, 1)
        self.assertIn("below the floor of 70 %", err)

    def test_the_floor_of_the_whole_app_is_checked_apart(self):
        path = self.write("report.xml", [(f"{BASE}/model/Models", 10, 0), (f"{BASE}/HomeScreensKt", 0, 90)])
        self.assertEqual(self.run_tool([str(path), "--floor", "70", "--all-floor", "5"])[0], 0)
        code, _, err = self.run_tool([str(path), "--floor", "70", "--all-floor", "20"])
        self.assertEqual(code, 1)
        self.assertIn("whole app", err)

    def test_without_floors_it_only_prints(self):
        path = self.write("report.xml", [(f"{BASE}/model/Models", 0, 10)])
        self.assertEqual(self.run_tool([str(path)])[0], 0)

    def test_exactly_the_floor_passes(self):
        path = self.write("report.xml", [(f"{BASE}/model/Models", 7, 3)])
        self.assertEqual(self.run_tool([str(path), "--floor", "70"])[0], 0)

    def test_a_folder_is_searched_for_the_report(self):
        self.write("build/reports/coverage/test/debug/report.xml", [(f"{BASE}/model/Models", 9, 1)])
        (self.root / "build" / "reports" / "coverage" / "test" / "debug" / "index.html").write_text("<html/>", encoding="utf-8")
        code, out, _ = self.run_tool([str(self.root / "build"), "--floor", "50"])
        self.assertEqual(code, 0)
        self.assertIn("90.0 %", out)

    def test_a_xml_file_that_is_not_a_report_is_passed_over(self):
        (self.root / "other.xml").write_text("<notes/>", encoding="utf-8")
        (self.root / "broken.xml").write_text("<a", encoding="utf-8")
        self.assertIsNone(C.find_report(self.root))
        self.write("report.xml", [(f"{BASE}/model/Models", 1, 1)])
        self.assertEqual(C.find_report(self.root).name, "report.xml")

    def test_no_report_is_an_error_of_the_check_and_not_a_pass(self):
        code, _, err = self.run_tool([str(self.root / "nothing"), "--floor", "1"])
        self.assertEqual(code, 2)
        self.assertIn("COVERAGE=ERROR", err)

    def test_a_report_that_cannot_be_parsed_is_an_error_of_the_check(self):
        path = self.root / "report.xml"
        path.write_text("<report><package", encoding="utf-8")
        code, _, err = self.run_tool([str(path)])
        self.assertEqual(code, 2)
        self.assertIn("cannot be read", err)

    def test_a_report_with_no_lines_in_the_rules_fails_because_something_was_renamed(self):
        path = self.write("report.xml", [(f"{BASE}/HomeScreensKt", 5, 5)])
        code, _, err = self.run_tool([str(path), "--floor", "1"])
        self.assertEqual(code, 1)
        self.assertIn("renamed", err)


if __name__ == "__main__":
    unittest.main()
