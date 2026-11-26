# EH3D-ANDROID-CONTROL - tools/test_check_languages.py: the check of the languages of the app, with small projects made for the test.   python -I tools/test_check_languages.py
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("check_languages", Path(__file__).resolve().parent / "check_languages.py")
C = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(C)

BUILD = 'android {\n    // localeFilters: a comment does not count\n    androidResources { localeFilters += listOf("en", "es", "de", "fr", "it", "ja", "zh-rCN", "pt-rPT") }\n}\n'


class Languages(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        (self.root / "app" / "src" / "main" / "res" / "values").mkdir(parents=True)
        self.build(BUILD)

    def tearDown(self):
        self._tmp.cleanup()

    def build(self, text):
        (self.root / "app" / "build.gradle.kts").write_text(text, encoding="utf-8")

    def folder(self, name, source="main"):
        (self.root / "app" / "src" / source / "res" / name).mkdir(parents=True)

    def test_the_eight_languages_and_folders_of_no_language_pass(self):
        for name in ("values-es", "values-pt-rPT", "values-zh-rCN", "values-b+zh+Hans", "values-v31", "values-night", "values-car", "drawable-hdpi", "mipmap-anydpi-v26", "values-sw600dp", "values-land"):
            self.folder(name)
        self.assertEqual(C.source_problems(self.root), [])

    def test_a_resource_folder_of_another_language_is_a_problem(self):
        for name in ("values-pl", "values-pt-rBR", "values-zh-rTW", "values-b+sr+Latn", "values-mcc214-ca", "drawable-nl-hdpi", "values-fil"):
            self.folder(name)
        self.folder("values-ru", source="debug")
        self.assertEqual(len(C.source_problems(self.root)), 8)

    def test_a_filter_too_many_missing_or_absent_is_a_problem(self):
        self.build(BUILD.replace('"pt-rPT")', '"pt-rPT", "pl")'))
        self.assertTrue(any("extra: pl" in p for p in C.source_problems(self.root)))
        self.build(BUILD.replace('"ja", ', ""))
        self.assertTrue(any("missing: ja" in p for p in C.source_problems(self.root)))
        self.build("android { }\n")
        self.assertTrue(any("localeFilters" in p for p in C.source_problems(self.root)))
        self.build(BUILD + 'android { defaultConfig { resourceConfigurations += "nl" } }\n')
        self.assertTrue(any("resourceConfigurations" in p for p in C.source_problems(self.root)))

    def test_the_apk_must_carry_the_eight_languages_and_no_other(self):
        good = "package: name='x'\nlocales: '--_--' 'de' 'en-GB' 'es' 'es-US' 'fr' 'fr-CA' 'it' 'ja' 'pt-PT' 'zh-CN'\n"
        self.assertEqual(C.apk_problems(good), [])
        for tag in ("pl", "pt-BR", "zh-TW", "zh_HK"):
            self.assertEqual(len(C.apk_problems(good.replace("'ja'", f"'ja' '{tag}'"))), 1, tag)
        self.assertTrue(any("no texts for: ja" in p for p in C.apk_problems(good.replace("'ja' ", ""))))
        self.assertTrue(any("locales line" in p for p in C.apk_problems("package: name='x'\n")))

    def test_the_project_of_this_repository_passes(self):
        self.assertEqual(C.source_problems(C.ROOT), [])


if __name__ == "__main__":
    unittest.main()
