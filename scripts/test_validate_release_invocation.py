#!/usr/bin/env python3
"""Tests for the release invocation policy."""

from __future__ import annotations

import unittest

from validate_release_invocation import (
    ReleaseInvocationError,
    validate_release_invocation,
)


class ReleaseInvocationTest(unittest.TestCase):
    def validate(self, **overrides: object) -> None:
        arguments: dict[str, object] = {
            "version": "1.0.0",
            "event_name": "workflow_dispatch",
            "ref": "refs/heads/main",
            "ref_name": "main",
            "default_branch": "main",
            "dry_run": False,
        }
        arguments.update(overrides)
        validate_release_invocation(**arguments)  # type: ignore[arg-type]

    def test_accepts_matching_stable_and_prerelease_tags(self) -> None:
        self.validate(
            event_name="push",
            ref="refs/tags/v1.0.0",
            ref_name="v1.0.0",
        )
        self.validate(
            version="1.1.0-rc1",
            event_name="push",
            ref="refs/tags/v1.1.0-rc1",
            ref_name="v1.1.0-rc1",
        )

    def test_accepts_manual_dry_run_from_a_feature_branch(self) -> None:
        self.validate(
            ref="refs/heads/release-experiment",
            ref_name="release-experiment",
            dry_run=True,
        )

    def test_rejects_invalid_release_versions(self) -> None:
        for version in ("1.0", "v1.0.0", "1.0.0-SNAPSHOT", "01.0.0", "1.0.0\nother=x"):
            with self.subTest(version=version):
                with self.assertRaises(ReleaseInvocationError):
                    self.validate(version=version)

    def test_rejects_a_tag_that_does_not_match_the_version(self) -> None:
        with self.assertRaises(ReleaseInvocationError):
            self.validate(
                event_name="push",
                ref="refs/tags/v1.0.1",
                ref_name="v1.0.1",
            )

    def test_rejects_non_dry_manual_release_from_an_untrusted_ref(self) -> None:
        with self.assertRaises(ReleaseInvocationError):
            self.validate(
                ref="refs/heads/release-experiment",
                ref_name="release-experiment",
            )


if __name__ == "__main__":
    unittest.main()
