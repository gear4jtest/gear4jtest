#!/usr/bin/env python3
"""Tests for release evidence generation."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from write_release_evidence import (
    REQUIRED_EVIDENCE,
    ReleaseEvidenceError,
    write_release_evidence,
)


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.repository_root = Path(self.temporary_directory.name)
        for relative_path in REQUIRED_EVIDENCE:
            path = self.repository_root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            content = (
                "matching artifact hashes\n"
                if relative_path.name in {"first.sha256", "second.sha256"}
                else f"evidence for {relative_path}\n"
            )
            path.write_text(content, encoding="utf-8")
        test_result = self.repository_root / "gear4jtest-core/build/test-results/test/TEST-Core.xml"
        test_result.parent.mkdir(parents=True, exist_ok=True)
        test_result.write_text("<testsuite tests=\"1\"/>\n", encoding="utf-8")

    def write(self, **overrides: object) -> tuple[Path, Path]:
        arguments: dict[str, object] = {
            "repository_root": self.repository_root,
            "output_directory": self.repository_root / "output",
            "version": "1.0.0",
            "repository": "gear4jtest/gear4jtest",
            "commit_sha": "a" * 40,
            "ref": "refs/tags/v1.0.0",
            "event_name": "push",
            "run_url": "https://github.com/gear4jtest/gear4jtest/actions/runs/1",
            "run_attempt": "1",
            "database_matrix_result": "success",
            "dry_run": False,
            "api_baseline_version": "",
        }
        arguments.update(overrides)
        return write_release_evidence(**arguments)  # type: ignore[arg-type]

    def test_writes_identity_gates_hashes_and_accepted_risk(self) -> None:
        json_path, markdown_path = self.write()

        manifest = json.loads(json_path.read_text(encoding="utf-8"))
        self.assertEqual("1.0.0", manifest["release"]["version"])
        self.assertEqual("a" * 40, manifest["release"]["commitSha"])
        self.assertEqual("success", manifest["gates"][0]["result"])
        self.assertIn(
            "authenticated-vulnerability-feed",
            {gate["name"] for gate in manifest["gates"]},
        )
        self.assertEqual(len(REQUIRED_EVIDENCE) + 1, len(manifest["evidenceFiles"]))
        self.assertEqual(64, len(manifest["evidenceFiles"][0]["sha256"]))
        self.assertIn(
            "docs/security/dependency-supply-chain.md",
            markdown_path.read_text(encoding="utf-8"),
        )

    def test_rejects_missing_evidence_and_failed_database_matrix(self) -> None:
        (self.repository_root / REQUIRED_EVIDENCE[0]).unlink()
        with self.assertRaises(ReleaseEvidenceError):
            self.write()

        path = self.repository_root / REQUIRED_EVIDENCE[0]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("restored\n", encoding="utf-8")
        with self.assertRaises(ReleaseEvidenceError):
            self.write(database_matrix_result="failure")

    def test_requires_compatibility_reports_when_a_baseline_is_configured(self) -> None:
        with self.assertRaises(ReleaseEvidenceError):
            self.write(version="1.0.1", api_baseline_version="1.0.0")

        report = self.repository_root / "build/reports/japicmp/gear4jtest-core.xml"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text("<report/>\n", encoding="utf-8")
        json_path, _ = self.write(version="1.0.1", api_baseline_version="1.0.0")
        manifest = json.loads(json_path.read_text(encoding="utf-8"))
        self.assertTrue(
            any(item["path"].endswith("gear4jtest-core.xml") for item in manifest["evidenceFiles"])
        )

    def test_rejects_snapshot_versions(self) -> None:
        with self.assertRaises(ReleaseEvidenceError):
            self.write(version="1.0.0-SNAPSHOT")
        with self.assertRaises(ReleaseEvidenceError):
            self.write(api_baseline_version="0.9.0-SNAPSHOT")


if __name__ == "__main__":
    unittest.main()
