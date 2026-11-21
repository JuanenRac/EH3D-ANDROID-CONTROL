# EH3D phone apps - tools/test_check_app_parity.py: the comparison of the examples, the catalog and the tables of languages of the two apps, with small data made for the test and with the real repository.
#   python -I tools/test_check_app_parity.py
import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("check_app_parity", HERE / "check_app_parity.py")
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)

PIN = next(iter(P.IOS_ONLY))


def side(examples=None, catalog=None, i18n=None, assets=None):
    return {"examples": examples or {"fleet.json": {"a": 1}}, "catalog": catalog or {"error-codes.json": {"codes": []}}, "i18n": i18n or {"en.json": {"Hola": "Hello"}},
            "assets": assets if assets is not None else {"settings-texts.json": {"k": {"en": "x"}}}}


class Reference(unittest.TestCase):
    def test_the_other_app_is_read_from_main_unless_a_branch_is_named(self):
        import os
        saved = os.environ.pop("EH3D_PARITY_REF", None)
        try:
            self.assertEqual(P.parity_ref(), "main")
            os.environ["EH3D_PARITY_REF"] = "  "
            self.assertEqual(P.parity_ref(), "main")
            os.environ["EH3D_PARITY_REF"] = "tmp/paired-change"
            self.assertEqual(P.parity_ref(), "tmp/paired-change")
        finally:
            os.environ.pop("EH3D_PARITY_REF", None)
            if saved is not None:
                os.environ["EH3D_PARITY_REF"] = saved


class Documents(unittest.TestCase):
    def test_equal_documents_have_no_problems(self):
        self.assertEqual(P.compare_documents("examples", {"a.json": {"x": [1, 2]}}, {"a.json": {"x": [1, 2]}}), [])

    def test_the_order_of_the_keys_does_not_matter(self):
        self.assertEqual(P.compare_documents("catalog", {"a.json": {"x": 1, "y": 2}}, {"a.json": {"y": 2, "x": 1}}), [])

    def test_a_document_in_one_app_only_is_named_with_its_side(self):
        found = P.compare_documents("examples", {"a.json": {}, "b.json": {}}, {"a.json": {}, "c.json": {}})
        self.assertEqual(found, ["examples: b.json is only in the Android app", "examples: c.json is only in the iPhone app"])

    def test_a_document_that_differs_is_named(self):
        self.assertEqual(P.compare_documents("examples", {"a.json": {"x": 1}}, {"a.json": {"x": 2}}), ["examples: a.json differs"])

    def test_nothing_on_both_sides_is_nothing_to_report(self):
        self.assertEqual(P.compare_documents("examples", {}, {}), [])


class Tables(unittest.TestCase):
    def test_the_same_tables_have_no_problems(self):
        table = {"en.json": {"Hola": "Hello", "Adios": "Bye"}}
        self.assertEqual(P.compare_tables(table, json.loads(json.dumps(table))), [])

    def test_a_language_in_one_app_only_is_named(self):
        found = P.compare_tables({"en.json": {}, "fr.json": {}}, {"en.json": {}, "de.json": {}})
        self.assertEqual(found, ["i18n: fr.json is only in the Android app", "i18n: de.json is only in the iPhone app"])

    def test_a_phrase_of_android_that_the_iphone_lacks_is_a_problem(self):
        found = P.compare_tables({"en.json": {"Hola": "Hello", "Nueva": "New"}}, {"en.json": {"Hola": "Hello"}})
        self.assertEqual(found, ["i18n: en.json: a phrase of the Android app is not in the iPhone app: Nueva"])

    def test_a_phrase_only_the_iphone_has_is_a_problem_unless_it_is_allowed(self):
        found = P.compare_tables({"en.json": {"Hola": "Hello"}}, {"en.json": {"Hola": "Hello", "Extra": "Extra"}})
        self.assertEqual(found, ["i18n: en.json: a phrase only the iPhone app has and is not allowed: Extra"])
        self.assertEqual(P.compare_tables({"en.json": {"Hola": "Hello"}}, {"en.json": {"Hola": "Hello", PIN: "pinned"}}), [])

    def test_a_translation_that_differs_is_a_problem(self):
        found = P.compare_tables({"fr.json": {"Hola": "Bonjour"}}, {"fr.json": {"Hola": "Salut"}})
        self.assertEqual(found, ["i18n: fr.json: the translation of a phrase differs: Hola"])

    def test_a_file_that_is_not_a_table_is_a_problem_and_not_a_crash(self):
        self.assertEqual(P.compare_tables({"en.json": ["a"]}, {"en.json": {"a": "b"}}), ["i18n: en.json is not a table of phrases"])

    def test_the_allowed_extra_phrases_have_a_reason(self):
        self.assertTrue(P.IOS_ONLY)
        self.assertTrue(all(reason for reason in P.IOS_ONLY.values()))

    def test_one_problem_per_phrase_and_language(self):
        found = P.compare_tables({"en.json": {"A": "1", "B": "2"}, "fr.json": {"A": "1", "B": "2"}}, {"en.json": {}, "fr.json": {}})
        self.assertEqual(len(found), 4)


class Everything(unittest.TestCase):
    def test_two_equal_sides_have_no_problems(self):
        self.assertEqual(P.compare_all(side(), side()), [])

    def test_each_part_is_compared(self):
        other = side(examples={"fleet.json": {"a": 2}}, catalog={"error-codes.json": {"codes": ["x"]}}, i18n={"en.json": {"Hola": "Hi"}})
        found = P.compare_all(side(), other)
        self.assertEqual(sorted(line.split(":")[0] for line in found), ["catalog", "examples", "i18n"])

    def test_the_files_of_the_settings_page_are_compared_as_data(self):
        other = side(assets={"settings-texts.json": {"k": {"en": "y"}}})
        self.assertEqual(P.compare_all(side(), other), ["assets: settings-texts.json differs"])
        self.assertEqual(P.compare_all(side(assets={}), side()), ["assets: settings-texts.json is only in the iPhone app"])
        self.assertEqual(P.compare_all(side(), side(assets={})), ["assets: settings-texts.json is only in the Android app"])

    def test_a_side_without_the_assets_key_is_still_compared(self):
        bare = side()
        del bare["assets"]
        self.assertEqual(P.compare_all(bare, bare), [])


class Reading(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def put(self, rel, name, data, raw=None):
        folder = self.root / rel
        folder.mkdir(parents=True, exist_ok=True)
        (folder / name).write_bytes(raw if raw is not None else json.dumps(data).encode("utf-8"))

    def test_a_folder_is_read_as_data_with_utf8_and_crlf(self):
        self.put("t", "a.json", None, raw='{\r\n  "Adiós": "Adeus"\r\n}\r\n'.encode("utf-8"))
        self.put("t", "b.txt", None, raw=b"not json")
        self.assertEqual(P.load_folder(self.root / "t"), {"a.json": {"Adiós": "Adeus"}})

    def test_a_side_is_read_from_the_places_of_its_app(self):
        where = P.APPS["android"]
        self.put(where["examples"], "fleet.json", {"m": 1})
        self.put(where["catalog"], "error-codes.json", {"c": 1})
        self.put(where["catalog"], "other.json", {"c": 2})
        self.put(where["i18n"], "en.json", {"Hola": "Hello"})
        self.put(where["assets"], "settings-contract.json", {"settings": []})
        self.put(where["assets"], "server-errors.json", {"x": ["a"]})
        got = P.read_side(self.root, "android")
        self.assertEqual(got["assets"], {"settings-contract.json": {"settings": []}})
        self.assertEqual(got["examples"], {"fleet.json": {"m": 1}})
        self.assertEqual(got["catalog"], {"error-codes.json": {"c": 1}})
        self.assertEqual(got["i18n"], {"en.json": {"Hola": "Hello"}})

    def test_the_side_of_a_repository_is_told_by_its_tables(self):
        self.assertIsNone(P.which_side(self.root))
        (self.root / P.APPS["ios"]["i18n"]).mkdir(parents=True)
        self.assertEqual(P.which_side(self.root), "ios")

    def test_a_broken_json_file_is_an_error_to_report(self):
        self.put("t", "a.json", None, raw=b"{")
        with self.assertRaises(ValueError):
            P.load_folder(self.root / "t")


class TheTwoApps(unittest.TestCase):
    """The two real repositories, when both are side by side."""

    def test_this_repository_agrees_with_its_sibling(self):
        root = HERE.parent
        side_key = P.which_side(root)
        self.assertIn(side_key, ("android", "ios"))
        other_key = "ios" if side_key == "android" else "android"
        sibling = root.parent / P.APPS[other_key]["repo"]
        if not (sibling / P.APPS[other_key]["i18n"]).is_dir():
            self.skipTest("the other app is not next to this repository")
        mine, other = P.read_side(root, side_key), P.read_side(sibling, other_key)
        sides = {side_key: mine, other_key: other}
        self.assertEqual(P.compare_all(sides["android"], sides["ios"]), [])

    def test_both_apps_carry_the_two_files_of_the_settings_page(self):
        found = P.read_side(HERE.parent, P.which_side(HERE.parent))["assets"]
        self.assertEqual(sorted(found), sorted(P.ASSET_FILES))

    def test_the_tables_are_seven_files_of_phrases_and_spanish_is_the_source(self):
        tables = P.load_folder(HERE.parent / P.APPS[P.which_side(HERE.parent)]["i18n"])
        self.assertEqual(len(tables), 7)
        self.assertEqual(sorted(tables), [f"{c}.json" for c in sorted(['en', 'it', 'de', 'fr', 'pt', 'zh', 'ja'])])
        self.assertNotIn("es.json", tables)
        self.assertIn("pt.json", tables)
        self.assertIn("en.json", tables)
        for name, table in tables.items():
            self.assertIsInstance(table, dict, name)
            self.assertTrue(all(isinstance(k, str) and isinstance(v, str) and v.strip() for k, v in table.items()), name)

    def test_every_complete_table_has_the_phrases_of_the_english_one_and_a_variant_has_no_others(self):
        tables = P.load_folder(HERE.parent / P.APPS[P.which_side(HERE.parent)]["i18n"])
        for name, table in tables.items():
            if "-" in name:
                self.assertLessEqual(set(table), set(tables["en.json"]), name)
            else:
                self.assertEqual(set(table), set(tables["en.json"]), name)

    def test_main_ends_with_zero_when_the_apps_are_side_by_side(self):
        root = HERE.parent
        side_key = P.which_side(root)
        other_key = "ios" if side_key == "android" else "android"
        if not (root.parent / P.APPS[other_key]["repo"] / P.APPS[other_key]["i18n"]).is_dir():
            self.skipTest("the other app is not next to this repository")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(P.main([]), 0)
        self.assertIn("OK:", out.getvalue())

    def test_main_names_a_folder_that_has_no_tables(self):
        with tempfile.TemporaryDirectory() as empty, contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(P.main(["--other", empty]), 2)
        self.assertIn("has no folder", out.getvalue())


if __name__ == "__main__":
    unittest.main()
