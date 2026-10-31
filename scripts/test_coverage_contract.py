"""Gate semantics, including the failures that CI must not round away or hide."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("coverage_contract", Path(__file__).parent / "coverage/coverage.py")
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)


class CoverageContractTest(unittest.TestCase):
    def test_threshold_is_exact_and_not_rounded(self):
        self.assertFalse(coverage.fraction(1999, 2500)["passed"])  # 79.96%
        self.assertTrue(coverage.fraction(2000, 2500)["passed"])
        self.assertFalse(coverage.fraction(0, 0)["passed"])

    def test_new_unexecuted_source_stays_in_denominator(self):
        scope = {"files": [{"module": "android", "path": "project/app/src/main/java/a/New.kt",
                            "change": "added", "candidate_line_ranges": [[1, 10]]}]}
        result = coverage.summarize(scope, {"android": {"a/New.kt": {2: False, 4: False}}})
        self.assertEqual((0, 2), (result["android"]["covered"], result["android"]["total"]))
        self.assertEqual([2, 4], result["android"]["files"][0]["uncovered_lines"])

    def test_modified_file_has_cumulative_scope_and_full_context(self):
        scope = {"files": [{"module": "android", "path": "project/app/src/main/java/a/Old.kt",
                            "change": "modified", "candidate_line_ranges": [[4, 5], [9, 9]]}]}
        result = coverage.summarize(scope, {"android": {"a/Old.kt": {2: False, 4: True, 9: False}}})
        self.assertEqual((1, 2), (result["android"]["covered"], result["android"]["total"]))
        self.assertEqual(3, result["android"]["files"][0]["whole_file"]["total"])

    def test_missing_module_and_source_are_errors(self):
        scope = {"files": [{"module": "android", "path": "project/app/src/main/java/a/New.kt",
                            "change": "added", "candidate_line_ranges": [[1, 10]]}]}
        for reports in ({}, {"android": {}}):
            with self.assertRaises(ValueError):
                coverage.summarize(scope, reports)

    def test_zero_or_missing_tests_cannot_pass(self):
        original = coverage.ROOT
        try:
            with tempfile.TemporaryDirectory() as directory:
                coverage.ROOT = Path(directory)
                with self.assertRaises(ValueError):
                    coverage.test_counts(["*.xml"])
                (coverage.ROOT / "zero.xml").write_text('<testsuite tests="0"/>')
                with self.assertRaises(ValueError):
                    coverage.test_counts(["*.xml"])
                (coverage.ROOT / "zero.xml").write_text('<testsuite tests="3" skipped="3"/>')
                with self.assertRaises(ValueError):
                    coverage.test_counts(["*.xml"])
        finally:
            coverage.ROOT = original

    def test_failure_counts_are_preserved(self):
        original = coverage.ROOT
        try:
            with tempfile.TemporaryDirectory() as directory:
                coverage.ROOT = Path(directory)
                (coverage.ROOT / "fail.xml").write_text('<testsuite tests="1" failures="1"/>')
                self.assertEqual(1, coverage.test_counts(["*.xml"])["failures"])
        finally:
            coverage.ROOT = original

    def test_java_kotlin_backend_and_web_entries_are_detected(self):
        self.assertEqual("android", coverage.module("project/app/src/main/java/a/discovery/New.kt"))
        self.assertEqual("android", coverage.module("project/app/src/main/kotlin/New.java"))
        self.assertEqual("backend", coverage.module("services/discovery/discovery/never_imported.py"))
        self.assertEqual("web", coverage.module("services/discovery/static/app.js"))
        self.assertIsNone(coverage.module("project/app/src/test/java/NewTest.kt"))


if __name__ == "__main__":
    unittest.main()
