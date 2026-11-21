# EH3D phone apps - tools/test_sync_settings_contract.py: the check of the copy of the settings contract and of the table of texts of the page, with small documents made for the test.   python -I tools/test_sync_settings_contract.py
import copy
import importlib.util
import json
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("sync_settings_contract", Path(__file__).resolve().parent / "sync_settings_contract.py")
S = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(S)

LANGS = ["en", "es", "de", "fr", "it", "ja", "zh", "pt"]


def words(text):
    return {lang: f"{text} {lang}" for lang in LANGS}


def setting(key, **extra):
    base = {"key": key, "group": "network", "type": "boolean", "default": False, "apply": "now", "write": {"via": "policy", "method": "PUT", "path": "/api/v1/auth/policy"},
            "role": "admin", "scope": "admin:policy", "confirm_when_looser": False, "secret": False, "risk": "none", "label": words("label"), "help": words("help"), "warning": words("warning")}
    base.update(extra)
    return base


def contract():
    return {"schema": S.SCHEMA, "languages": list(LANGS), "groups": [{"id": "network", "label": words("Network")}], "settings": [setting("a"), setting("b", type="integer", min=1, max=9, default=3)]}


class SchemaTests(unittest.TestCase):
    def test_a_good_contract_has_no_problems(self):
        self.assertEqual(S.schema_problems(contract()), [])

    def test_the_real_copy_is_a_good_contract_with_unique_keys_and_the_eight_languages(self):
        doc = json.loads(S.COPY.read_text(encoding="utf-8"))
        self.assertEqual(S.schema_problems(doc), [])
        keys = [s["key"] for s in doc["settings"]]
        self.assertEqual(len(keys), len(set(keys)))
        self.assertEqual(doc["languages"], LANGS)

    def test_another_schema_is_refused(self):
        doc = contract()
        doc["schema"] = "eh3d.settings.contract/2"
        self.assertTrue(S.schema_problems(doc)[0].startswith("schema is"))

    def test_a_missing_language_is_found(self):
        doc = contract()
        del doc["settings"][0]["label"]["pt"]
        self.assertIn("a: 'label' has no text in pt", S.schema_problems(doc))

    def test_a_repeated_key_a_wrong_group_a_wrong_type_and_a_wrong_risk_are_found(self):
        doc = contract()
        doc["settings"].append(setting("a"))
        doc["settings"].append(setting("c", group="nowhere", type="float", risk="odd"))
        text = "\n".join(S.schema_problems(doc))
        self.assertIn("a: the key is repeated", text)
        self.assertIn("c: group 'nowhere' is not one of the groups", text)
        self.assertIn("c: type 'float'", text)
        self.assertIn("c: risk 'odd'", text)

    def test_an_integer_needs_its_range_and_an_enum_its_values(self):
        doc = contract()
        doc["settings"].append(setting("n", type="integer"))
        doc["settings"].append(setting("m", type="integer", min=5, max=1))
        doc["settings"].append(setting("e", type="enum"))
        text = "\n".join(S.schema_problems(doc))
        self.assertIn("n: an integer without min and max", text)
        self.assertIn("m: min 5 is over max 1", text)
        self.assertIn("e: an enum without values", text)

    def test_a_key_of_the_policy_needs_its_route(self):
        doc = contract()
        doc["settings"].append(setting("p", write={"via": "policy", "method": None, "path": None}))
        self.assertIn("p: a key written by the policy has no method and path", S.schema_problems(doc))

    def test_a_key_set_in_the_file_needs_no_route(self):
        doc = contract()
        doc["settings"].append(setting("f", write={"via": "file", "method": None, "path": None, "file": "server.cfg"}))
        self.assertEqual(S.schema_problems(doc), [])

    def test_the_languages_must_be_the_eight_in_order(self):
        doc = contract()
        doc["languages"] = ["en", "es"]
        self.assertTrue(any(p.startswith("languages are") for p in S.schema_problems(doc)))

    def test_a_secret_flag_must_be_true_or_false(self):
        doc = contract()
        doc["settings"][0]["secret"] = "no"
        self.assertIn("a: secret is not true or false", S.schema_problems(doc))


class TextsTests(unittest.TestCase):
    def table(self):
        return {"k": words("one {n}"), "j": words("two")}

    def test_a_good_table_has_no_problems(self):
        self.assertEqual(S.texts_problems(self.table()), [])

    def test_the_real_table_is_good(self):
        self.assertEqual(S.texts_problems(json.loads(S.TEXTS.read_text(encoding="utf-8"))), [])

    def test_a_missing_language_a_blank_text_and_a_changed_hole_are_found(self):
        table = self.table()
        del table["j"]["ja"]
        table["k"]["de"] = " "
        text = "\n".join(S.texts_problems(table))
        self.assertIn("j: has the languages", text)
        self.assertIn("k: blank text in de", text)
        table = self.table()
        table["k"]["fr"] = "un {x}"
        self.assertIn("k: the holes of fr are ['{x}']", "\n".join(S.texts_problems(table)))

    def test_an_empty_table_is_refused(self):
        self.assertEqual(S.texts_problems({}), ["settings-texts.json is not a table of texts"])
        self.assertEqual(S.texts_problems(copy.deepcopy([])), ["settings-texts.json is not a table of texts"])


class HashTests(unittest.TestCase):
    def test_the_hash_is_the_sha256_of_the_copy_with_lf_line_ends(self):
        import hashlib
        import tempfile

        with tempfile.TemporaryDirectory() as folder:
            lf = Path(folder) / "lf.json"
            crlf = Path(folder) / "crlf.json"
            lf.write_bytes(b'{"a": 1}\n{"b": 2}\n')
            crlf.write_bytes(b'{"a": 1}\r\n{"b": 2}\r\n')
            self.assertEqual(S.contract_hash(lf), hashlib.sha256(b'{"a": 1}\n{"b": 2}\n').hexdigest())
            self.assertEqual(S.contract_hash(crlf), S.contract_hash(lf))

    def test_the_hash_of_the_real_copy_is_64_lower_case_hexadecimal_characters(self):
        self.assertRegex(S.contract_hash(S.COPY), r"^[0-9a-f]{64}$")

    def test_the_source_file_holds_that_hash_in_kotlin_or_in_swift(self):
        digest = "ab" * 32
        kotlin = S.hash_file_text(digest, swift=False)
        swift = S.hash_file_text(digest, swift=True)
        self.assertIn(f'const val CONTRACT_COPY_HASH = "{digest}"', kotlin)
        self.assertIn("package es.electrohobby3d.eh3d.model", kotlin)
        self.assertIn(f'public let contractCopyHash = "{digest}"', swift)
        self.assertNotIn("package ", swift)
        self.assertTrue(kotlin.endswith('"\n') and swift.endswith('"\n'))

    def test_the_real_hash_file_is_the_hash_of_the_copy(self):
        self.assertIsNone(S.hash_problem())
        self.assertIn(S.contract_hash(S.COPY), S.HASH_FILE.read_text(encoding="utf-8"))

    def test_a_stale_or_missing_hash_file_is_found(self):
        import tempfile

        original, root = S.HASH_FILE, S.ROOT
        try:
            with tempfile.TemporaryDirectory() as folder:
                S.ROOT = Path(folder)
                S.HASH_FILE = Path(folder) / "ContractHash.txt"
                self.assertIn("is not the hash of the copy", S.hash_problem() or "")
                S.HASH_FILE.write_text(S.hash_file_text("0" * 64), encoding="utf-8")
                self.assertIn("is not the hash of the copy", S.hash_problem() or "")
                S.HASH_FILE.write_text(S.hash_file_text(S.contract_hash(S.COPY)).replace("\n", "\r\n"), encoding="utf-8", newline="")
                self.assertIsNone(S.hash_problem())
        finally:
            S.HASH_FILE, S.ROOT = original, root


class FilesTests(unittest.TestCase):
    def test_the_check_passes_on_this_repository(self):
        self.assertEqual(S.check(), 0)

    def test_the_line_ends_do_not_matter(self):
        self.assertEqual(S.normalised(S.COPY), S.COPY.read_bytes().decode("utf-8").replace("\r\n", "\n"))

    def test_the_help_exits_with_zero(self):
        with self.assertRaises(SystemExit) as raised:
            S.main(["--help"])
        self.assertEqual(raised.exception.code, 0)


if __name__ == "__main__":
    unittest.main()
