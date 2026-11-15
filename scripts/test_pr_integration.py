"""Exercise real Git histories and the critical CI trigger/security contract."""
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/pr_integration.py"
spec = importlib.util.spec_from_file_location("pr_integration", SCRIPT)
integration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(integration)


class IntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Process regression")
        self.git("config", "user.email", "process-test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.base = self.commit("shared.txt", "base\n")
        self.git("switch", "-c", "topic")
        self.head = self.commit("topic.txt", "feature\n")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo, stderr=subprocess.PIPE,
                                       text=True, encoding="utf-8").strip()

    def commit(self, name, content):
        (self.repo / name).write_text(content, encoding="utf-8")
        self.git("add", name)
        self.git("commit", "-m", name)
        return self.git("rev-parse", "HEAD")

    def run_cli(self, base, head):
        report = self.repo / ".git/integration.json"
        environment = os.environ.copy()
        environment.pop("GITHUB_STEP_SUMMARY", None)
        result = subprocess.run([sys.executable, str(SCRIPT), "--base", base, "--head", head,
                                 "--output", str(report)], cwd=self.repo, capture_output=True,
                                text=True, encoding="utf-8", env=environment)
        return result, json.loads(report.read_text(encoding="utf-8"))

    def test_fresh_head_receipt_matches_actual_git_tree_without_mutation(self):
        result, receipt = self.run_cli(self.base, self.head)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.git("rev-parse", "HEAD^{tree}"), receipt["candidate_tree"])
        self.assertEqual(self.head, receipt["checkout_sha"])
        self.assertTrue(receipt["candidate_is_tested_head"])
        self.assertTrue(receipt["full_ci_required"])
        self.assertTrue(receipt["human_review_required"])
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_clean_mergeable_but_stale_branch_fails_until_real_integration(self):
        self.git("switch", "main")
        newer = self.commit("other.txt", "independent base change\n")
        self.git("switch", "topic")
        result, receipt = self.run_cli(newer, self.head)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("failure", receipt["status"])
        self.assertIn("Integrate that base", result.stderr)
        self.assertEqual(self.head, self.git("rev-parse", "HEAD"))
        self.git("merge", "--no-edit", "main")
        merged = self.git("rev-parse", "HEAD")
        result, receipt = self.run_cli(newer, merged)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(newer, receipt["base_sha"])

    def test_conflicting_target_does_not_get_auto_resolved(self):
        self.head = self.commit("shared.txt", "topic version\n")
        self.git("switch", "main")
        newer = self.commit("shared.txt", "base version\n")
        self.git("switch", "topic")
        result, _ = self.run_cli(newer, self.head)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("topic version\n", (self.repo / "shared.txt").read_text(encoding="utf-8"))
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_wrong_checkout_rejects_other_revisions_evidence(self):
        self.git("checkout", "--detach", self.base)
        with self.assertRaisesRegex(integration.IntegrationError, "expected PR head"):
            integration.inspect(self.repo, self.base, self.head)

    def test_modified_and_untracked_source_are_rejected(self):
        for name in ("topic.txt", "untracked.kt"):
            with self.subTest(name=name):
                (self.repo / name).write_text("uncommitted\n", encoding="utf-8")
                with self.assertRaisesRegex(integration.IntegrationError, "dirty"):
                    integration.inspect(self.repo, self.base, self.head)
                if name == "topic.txt":
                    self.git("restore", name)
                else:
                    (self.repo / name).unlink()

    def test_invalid_or_missing_commit_never_passes(self):
        for value in ("main", "--help", "$(touch injected)", "0" * 40):
            with self.subTest(value=value):
                result, receipt = self.run_cli(value, self.head)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("failure", receipt["status"])
                self.assertNotIn("candidate_tree", receipt)
        self.assertFalse((self.repo / "injected").exists())

    def test_unrelated_history_fails_even_if_both_commits_exist(self):
        self.git("switch", "--orphan", "unrelated")
        unrelated = self.commit("unrelated.txt", "different root\n")
        self.git("switch", "topic")
        with self.assertRaisesRegex(integration.IntegrationError, "does not include"):
            integration.inspect(self.repo, unrelated, self.head)

    def test_tag_object_is_not_a_commit_identity(self):
        self.git("-c", "tag.gpgsign=false", "tag", "-a", "snapshot", "-m", "snapshot", self.base)
        tag_object = self.git("rev-parse", "snapshot")
        with self.assertRaisesRegex(integration.IntegrationError, "tag objects"):
            integration.inspect(self.repo, tag_object, self.head)

    def test_shallow_history_cannot_certify_integration(self):
        with tempfile.TemporaryDirectory() as directory:
            clone = Path(directory) / "shallow"
            subprocess.run(["git", "clone", "--quiet", "--depth", "1", "--branch", "topic",
                            self.repo.as_uri(), str(clone)], check=True, capture_output=True)
            with self.assertRaisesRegex(integration.IntegrationError, "Shallow"):
                integration.inspect(clone, self.head, self.head)


class WorkflowContractTest(unittest.TestCase):
    def test_full_ci_and_integration_accept_stacked_target_branches(self):
        for name in ("android.yml", "pr-integration.yml"):
            text = (ROOT / ".github/workflows" / name).read_text(encoding="utf-8")
            block = re.search(r"(?ms)^  pull_request:\n(.*?)(?=^  \S|^\S)", text).group(1)
            for excluded in ("branches:", "branches-ignore:", "paths:", "paths-ignore:"):
                self.assertNotIn(excluded, block)
            self.assertNotIn("pull_request_target:", text)
            self.assertIn("persist-credentials: false", text)
        integration_workflow = (ROOT / ".github/workflows/pr-integration.yml").read_text(encoding="utf-8")
        self.assertIn("types: [opened, synchronize, reopened, edited]", integration_workflow)
        self.assertIn("PR_BASE_SHA: ${{ github.event.pull_request.base.sha }}", integration_workflow)
        self.assertIn("PR_HEAD_SHA: ${{ github.event.pull_request.head.sha }}", integration_workflow)
        self.assertNotIn(": write", integration_workflow)
        self.assertNotIn("secrets.", integration_workflow)

    def test_ci_keeps_full_gate_artifacts_and_direct_guard_tests(self):
        text = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        for required in ("python scripts/test_pr_integration.py", "python scripts/test_coverage_contract.py",
                         "python scripts/coverage/coverage.py report", ":app:connectedDebugAndroidTest",
                         "pnpm run test:coverage", "python -m pytest --cov=discovery",
                         "team-coverage-${{ github.run_id }}", "app-debug-${{ github.run_id }}"):
            self.assertIn(required, text)
        self.assertNotIn("continue-on-error: true", text)
        manifest = json.loads((ROOT / "docs/testing/coverage-scope.json").read_text(encoding="utf-8"))
        self.assertEqual(80, manifest["minimum_line_percent"])
        self.assertEqual("947709e4c798a103b9fcc5962172f2e85d349196", manifest["baseline"])


if __name__ == "__main__":
    unittest.main()
