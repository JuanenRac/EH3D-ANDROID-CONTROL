"""Tests of tools/third_party_notices.py with a made-up graph (no Gradle, no network)."""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import third_party_notices as notices  # noqa: E402

GRAPH = r"""releaseRuntimeClasspath - Resolved configuration for runtimeClasspath
+--- androidx.compose:compose-bom:2026.09.00
|    +--- androidx.compose.ui:ui:1.9.0 (c)
+--- org.example:alpha -> 1.2.0
|    \--- org.example:beta:{strictly 2.0} -> 2.0 (c)
+--- org.example:gamma:1.0 -> 1.1
|    \--- org.example:delta:3.0 (*)
\--- androidx.compose.ui:ui -> 1.9.0
"""


class Notices(unittest.TestCase):
    def test_the_graph_gives_the_libraries_with_the_chosen_version(self) -> None:
        self.assertEqual(notices.graph(GRAPH), [("androidx.compose.ui", "ui", "1.9.0"), ("org.example", "alpha", "1.2.0"),
                                                ("org.example", "delta", "3.0"), ("org.example", "gamma", "1.1")])

    def test_a_text_that_is_not_a_graph_is_refused(self) -> None:
        with self.assertRaises(SystemExit):
            notices.graph("BUILD FAILED")

    def test_other_terms_are_accepted_by_family_only(self) -> None:
        accepted, terms = notices.accepted, notices.SDK_TERMS
        self.assertEqual(accepted("com.google.android.gms", "com.google.android.gms:play-services-base", [terms]), terms)
        self.assertIsNone(accepted("org.example", "org.example:thing", [terms]))
        self.assertEqual(accepted("androidx.datastore", "androidx.datastore:datastore-preferences-external-protobuf", ["BSD-3-Clause"]), "BSD-3-Clause")
        self.assertIsNone(accepted("org.example", "org.example:thing", ["BSD-3-Clause"]))
        self.assertIsNone(accepted("org.example", "org.example:thing", ["GPL-2.0"]))

    def test_a_library_that_is_not_listed_fails_the_check(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            deps = Path(folder) / "deps.txt"
            deps.write_text(GRAPH, encoding="utf-8")
            file = Path(folder) / "THIRD_PARTY_NOTICES"
            rows = "".join(f"{g}:{a} | {v} | Apache-2.0\n" for g, a, v in notices.graph(GRAPH))
            file.write_text(f"{notices.MARKER}\n\nComponents\n----------\n\n{rows}\nCopyright\n", encoding="utf-8")
            self.assertEqual(notices.main([str(deps), "--check", "--output", str(file)]), 0)
            deps.write_text(GRAPH + "+--- org.example:epsilon:1.0\n", encoding="utf-8")
            self.assertEqual(notices.main([str(deps), "--check", "--output", str(file)]), 1)
            self.assertEqual(notices.main([str(deps), "--check", "--output", str(file) + ".none"]), 1)


if __name__ == "__main__":
    unittest.main()
