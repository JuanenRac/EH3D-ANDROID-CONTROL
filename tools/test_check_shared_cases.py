# EH3D phone apps - tools/test_check_shared_cases.py: the comparison of the shared case files, with small folders made for the test.   python -I tools/test_check_shared_cases.py
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("check_shared_cases", Path(__file__).resolve().parent / "check_shared_cases.py")
C = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(C)


class Compare(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def folder(self, name, files):
        path = self.root / name
        path.mkdir()
        for file, text in files.items():
            (path / file).write_bytes(text)
        return path

    def test_the_same_files_have_no_problems(self):
        a = self.folder("a", {"x.json": b"{}\n", "y.json": b"[1]\n"})
        b = self.folder("b", {"x.json": b"{}\n", "y.json": b"[1]\n"})
        self.assertEqual(C.compare(C.digests(a), C.digests(b)), [])

    def test_a_file_that_differs_is_named(self):
        a = self.folder("a", {"x.json": b"{}\n"})
        b = self.folder("b", {"x.json": b'{"a":1}\n'})
        self.assertEqual(C.compare(C.digests(a), C.digests(b)), ["x.json: differs"])

    def test_a_file_missing_on_either_side_is_named(self):
        a = self.folder("a", {"x.json": b"{}\n", "only_a.json": b"1"})
        b = self.folder("b", {"x.json": b"{}\n", "only_b.json": b"2"})
        self.assertEqual(
            C.compare(C.digests(a), C.digests(b)),
            ["only_a.json: only in this repository", "only_b.json: only in the other repository"],
        )

    def test_the_end_of_line_of_the_system_does_not_matter(self):
        a = self.folder("a", {"x.json": b"{\r\n}\r\n"})
        b = self.folder("b", {"x.json": b"{\n}\n"})
        self.assertEqual(C.compare(C.digests(a), C.digests(b)), [])

    def test_only_json_files_are_read(self):
        a = self.folder("a", {"x.json": b"{}", "notes.txt": b"hi"})
        self.assertEqual(list(C.digests(a)), ["x.json"])

    def test_the_side_is_told_from_the_folder(self):
        android = self.root / "android"
        (android / "app/src/test/resources/shared-cases").mkdir(parents=True)
        ios = self.root / "ios"
        (ios / "Tests/Eh3dCoreTests/Cases").mkdir(parents=True)
        self.assertEqual(C.which_side(android), "android")
        self.assertEqual(C.which_side(ios), "ios")
        self.assertIsNone(C.which_side(self.root / "none"))


if __name__ == "__main__":
    unittest.main()
