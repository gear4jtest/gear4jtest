#!/usr/bin/env python3
"""Validate a tag or manual invocation before release work starts."""

from __future__ import annotations

import argparse
import re
import sys


RELEASE_VERSION = re.compile(
    r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"
)


class ReleaseInvocationError(ValueError):
    """Raised when a workflow invocation is unsafe or inconsistent."""


def validate_release_invocation(
    *,
    version: str,
    event_name: str,
    ref: str,
    ref_name: str,
    default_branch: str,
    dry_run: bool,
) -> None:
    if not RELEASE_VERSION.fullmatch(version) or "SNAPSHOT" in version.upper():
        raise ReleaseInvocationError(
            "Release version must be a SemVer-like value such as 1.0.0 or "
            "1.0.0-rc1; snapshots and a leading 'v' are forbidden"
        )
    if event_name not in {"push", "workflow_dispatch"}:
        raise ReleaseInvocationError(f"Unsupported release event: {event_name}")
    if not default_branch or "\n" in default_branch or "\r" in default_branch:
        raise ReleaseInvocationError("Repository default branch is missing or invalid")

    expected_tag = f"v{version}"
    expected_tag_ref = f"refs/tags/{expected_tag}"
    if event_name == "push":
        if ref != expected_tag_ref or ref_name != expected_tag:
            raise ReleaseInvocationError(
                f"Tag release must use {expected_tag_ref}; received {ref} ({ref_name})"
            )
        return

    if dry_run:
        return

    allowed_refs = {f"refs/heads/{default_branch}", expected_tag_ref}
    if ref not in allowed_refs:
        raise ReleaseInvocationError(
            "A non-dry manual release must run from the default branch or the "
            f"matching {expected_tag} tag; received {ref}"
        )


def parse_boolean(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise argparse.ArgumentTypeError("expected true or false")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--version", required=True)
    result.add_argument("--event-name", required=True)
    result.add_argument("--ref", required=True)
    result.add_argument("--ref-name", required=True)
    result.add_argument("--default-branch", required=True)
    result.add_argument("--dry-run", required=True, type=parse_boolean)
    return result


def main() -> int:
    arguments = parser().parse_args()
    try:
        validate_release_invocation(
            version=arguments.version,
            event_name=arguments.event_name,
            ref=arguments.ref,
            ref_name=arguments.ref_name,
            default_branch=arguments.default_branch,
            dry_run=arguments.dry_run,
        )
    except ReleaseInvocationError as error:
        print(f"Release invocation rejected: {error}", file=sys.stderr)
        return 1
    print(f"Release invocation validated for {arguments.version}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
