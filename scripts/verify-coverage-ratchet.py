#!/usr/bin/env python3
"""Reject coverage-threshold reductions against a Git base revision."""

from __future__ import annotations

import json
import subprocess
import sys
from decimal import Decimal
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
POLICIES = (
    (
        Path("config/module-coverage-thresholds.json"),
        "modules",
        "projectPath",
        "minimumLineRatio",
    ),
    (
        Path("config/critical-coverage-thresholds.json"),
        "classes",
        "className",
        "minimumBranchRatio",
    ),
)


def fail(message: str) -> None:
    print(message, file=sys.stderr)


def load_current(path: Path) -> dict[str, Any]:
    with (ROOT / path).open(encoding="utf-8") as stream:
        return json.load(stream, parse_float=Decimal)


def load_at_revision(revision: str, path: Path) -> dict[str, Any]:
    result = subprocess.run(
        ["git", "show", f"{revision}:{path.as_posix()}"],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Cannot read {path} at {revision}: {result.stderr.strip()}"
        )
    return json.loads(result.stdout, parse_float=Decimal)


def index_policy(
    policy: dict[str, Any], collection: str, identity: str, ratio: str
) -> dict[str, Decimal]:
    indexed: dict[str, Decimal] = {}
    for entry in policy.get(collection, []):
        key = entry.get(identity)
        value = entry.get(ratio)
        if not isinstance(key, str) or not key:
            raise ValueError(f"Invalid or missing {identity} in {collection}")
        if key in indexed:
            raise ValueError(f"Duplicate {identity} in {collection}: {key}")
        if not isinstance(value, Decimal):
            value = Decimal(str(value))
        indexed[key] = value
    return indexed


def main() -> int:
    if len(sys.argv) != 2:
        fail("Usage: scripts/verify-coverage-ratchet.py <git-base-revision>")
        return 2

    base_revision = sys.argv[1]
    failures: list[str] = []
    try:
        for path, collection, identity, ratio in POLICIES:
            base = index_policy(
                load_at_revision(base_revision, path), collection, identity, ratio
            )
            current = index_policy(load_current(path), collection, identity, ratio)
            for key, base_ratio in sorted(base.items()):
                current_ratio = current.get(key)
                if current_ratio is None:
                    failures.append(f"{path}: removed ratchet {key}")
                elif current_ratio < base_ratio:
                    failures.append(
                        f"{path}: {key} decreased from {base_ratio} to {current_ratio}"
                    )
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        fail(f"Coverage ratchet verification failed: {error}")
        return 2

    if failures:
        fail("Coverage ratchets must not decrease:\n - " + "\n - ".join(failures))
        return 1

    print(f"Coverage ratchets do not decrease relative to {base_revision}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
