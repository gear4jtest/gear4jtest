#!/usr/bin/env python3
"""Write machine-readable and human-readable evidence for a completed release run."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from validate_release_invocation import RELEASE_VERSION, parse_boolean


REQUIRED_EVIDENCE = (
    Path("build/reports/release/staged-artifacts.txt"),
    Path("build/reports/release/vulnerability-feed.txt"),
    Path("build/reports/reproducibility/first.sha256"),
    Path("build/reports/reproducibility/second.sha256"),
    Path("build/reports/dependency-check-report.json"),
    Path("build/reports/jacoco/report.xml"),
    Path("gear4jtest-core/build/reports/jmh/results.json"),
    Path("gear4jtest-jdbc/build/reports/sql-plan-qualification/postgresql.md"),
    Path("gear4jtest-jdbc/build/reports/sql-plan-qualification/mysql.md"),
    Path("gear4jtest-jdbc/build/reports/sql-plan-qualification/mariadb.md"),
    Path("gear4jtest-jdbc/build/reports/sql-plan-qualification/oracle.md"),
    Path("config/module-coverage-thresholds.json"),
    Path("config/critical-coverage-thresholds.json"),
    Path("config/performance-budgets.json"),
    Path("docs/security/dependency-supply-chain.md"),
    Path("docs/production-readiness.md"),
)


class ReleaseEvidenceError(ValueError):
    """Raised when release evidence is incomplete or inconsistent."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def evidence_file(repository_root: Path, relative_path: Path) -> dict[str, Any]:
    path = repository_root / relative_path
    if not path.is_file():
        raise ReleaseEvidenceError(f"Missing release evidence: {relative_path}")
    if path.stat().st_size == 0:
        raise ReleaseEvidenceError(f"Release evidence is empty: {relative_path}")
    return {
        "path": relative_path.as_posix(),
        "sizeBytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def markdown_cell(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def write_release_evidence(
    *,
    repository_root: Path,
    output_directory: Path,
    version: str,
    repository: str,
    commit_sha: str,
    ref: str,
    event_name: str,
    run_url: str,
    run_attempt: str,
    database_matrix_result: str,
    dry_run: bool,
    api_baseline_version: str,
) -> tuple[Path, Path]:
    if not RELEASE_VERSION.fullmatch(version) or "SNAPSHOT" in version.upper():
        raise ReleaseEvidenceError(f"Invalid release version: {version}")
    if api_baseline_version and (
        not RELEASE_VERSION.fullmatch(api_baseline_version)
        or "SNAPSHOT" in api_baseline_version.upper()
    ):
        raise ReleaseEvidenceError(f"Invalid API baseline version: {api_baseline_version}")
    if not re_full_sha(commit_sha):
        raise ReleaseEvidenceError("Commit SHA must contain exactly 40 hexadecimal characters")
    if database_matrix_result != "success":
        raise ReleaseEvidenceError(
            f"Database matrix is not successful: {database_matrix_result}"
        )

    files = [evidence_file(repository_root, path) for path in REQUIRED_EVIDENCE]
    first_hashes = repository_root / "build/reports/reproducibility/first.sha256"
    second_hashes = repository_root / "build/reports/reproducibility/second.sha256"
    if first_hashes.read_bytes() != second_hashes.read_bytes():
        raise ReleaseEvidenceError("Reproducibility hash inventories do not match")
    test_results = sorted(
        path
        for path in repository_root.glob(
            "gear4jtest-*/build/test-results/**/*.xml"
        )
        if path.is_file()
    )
    if not test_results:
        raise ReleaseEvidenceError("No JUnit XML release evidence was preserved")
    files.extend(
        evidence_file(repository_root, path.relative_to(repository_root))
        for path in test_results
    )
    if api_baseline_version:
        compatibility_reports = sorted(
            (repository_root / "build/reports/japicmp").glob("*.xml")
        )
        if not compatibility_reports:
            raise ReleaseEvidenceError(
                "An API baseline was configured but no Japicmp report was produced"
            )
        files.extend(
            evidence_file(repository_root, path.relative_to(repository_root))
            for path in compatibility_reports
        )

    generated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    manifest = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "release": {
            "version": version,
            "repository": repository,
            "commitSha": commit_sha.lower(),
            "ref": ref,
            "eventName": event_name,
            "workflowRunUrl": run_url,
            "workflowRunAttempt": run_attempt,
            "dryRun": dry_run,
            "apiBaselineVersion": api_baseline_version or None,
        },
        "gates": [
            {"name": "database-matrix", "result": database_matrix_result},
            {"name": "authenticated-vulnerability-feed", "result": "success"},
            {"name": "coverage-report", "result": "success"},
            {"name": "releaseCheck", "result": "success"},
            {"name": "reproducible-staging", "result": "success"},
            {
                "name": "jreleaserDeploy",
                "result": "success",
                "mode": "dry-run" if dry_run else "publish",
            },
        ],
        "evidenceFiles": files,
        "acceptedRisks": [
            {
                "id": "advanced-dependency-verification-deferred-post-1.0",
                "reference": "docs/security/dependency-supply-chain.md",
            }
        ],
        "operationalBoundaries": [
            "docs/production-readiness.md",
            "docs/runtime/runtime-guarantees.md",
        ],
    }

    output_directory.mkdir(parents=True, exist_ok=True)
    json_path = output_directory / "release-evidence.json"
    markdown_path = output_directory / "release-evidence.md"
    json_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    markdown_lines = [
        f"# Release evidence — {version}",
        "",
        "| Field | Value |",
        "| --- | --- |",
        f"| Generated | {markdown_cell(generated_at)} |",
        f"| Repository | {markdown_cell(repository)} |",
        f"| Commit | `{markdown_cell(commit_sha.lower())}` |",
        f"| Ref | `{markdown_cell(ref)}` |",
        f"| Workflow run | {markdown_cell(run_url)} |",
        f"| Attempt | {markdown_cell(run_attempt)} |",
        f"| Mode | {'dry-run' if dry_run else 'publish'} |",
        f"| API baseline | {markdown_cell(api_baseline_version or 'none (allowed for 1.0.0/prerelease)')} |",
        "",
        "## Gates",
        "",
    ]
    markdown_lines.extend(
        f"- `{gate['name']}`: **{gate['result']}**"
        + (f" ({gate['mode']})" if "mode" in gate else "")
        for gate in manifest["gates"]
    )
    markdown_lines.extend(
        [
            "",
            "## Evidence files",
            "",
            "| Path | Bytes | SHA-256 |",
            "| --- | ---: | --- |",
        ]
    )
    markdown_lines.extend(
        f"| `{markdown_cell(item['path'])}` | {item['sizeBytes']} | `{item['sha256']}` |"
        for item in files
    )
    markdown_lines.extend(
        [
            "",
            "## Accepted risk",
            "",
            "Advanced dependency locking and Gradle verification metadata remain deferred after 1.0, as recorded in "
            "`docs/security/dependency-supply-chain.md`.",
            "",
        ]
    )
    markdown_path.write_text("\n".join(markdown_lines), encoding="utf-8")
    return json_path, markdown_path


def re_full_sha(value: str) -> bool:
    return len(value) == 40 and all(character in "0123456789abcdefABCDEF" for character in value)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--repository-root", type=Path, default=Path.cwd())
    result.add_argument("--output-directory", type=Path)
    result.add_argument("--version", required=True)
    result.add_argument("--repository", required=True)
    result.add_argument("--commit-sha", required=True)
    result.add_argument("--ref", required=True)
    result.add_argument("--event-name", required=True)
    result.add_argument("--run-url", required=True)
    result.add_argument("--run-attempt", required=True)
    result.add_argument("--database-matrix-result", required=True)
    result.add_argument("--dry-run", required=True, type=parse_boolean)
    result.add_argument("--api-baseline-version", default="")
    return result


def main() -> int:
    arguments = parser().parse_args()
    repository_root = arguments.repository_root.resolve()
    output_directory = arguments.output_directory or repository_root / "build/reports/release"
    try:
        json_path, markdown_path = write_release_evidence(
            repository_root=repository_root,
            output_directory=output_directory,
            version=arguments.version,
            repository=arguments.repository,
            commit_sha=arguments.commit_sha,
            ref=arguments.ref,
            event_name=arguments.event_name,
            run_url=arguments.run_url,
            run_attempt=arguments.run_attempt,
            database_matrix_result=arguments.database_matrix_result,
            dry_run=arguments.dry_run,
            api_baseline_version=arguments.api_baseline_version,
        )
    except (OSError, ReleaseEvidenceError) as error:
        print(f"Release evidence generation failed: {error}", file=sys.stderr)
        return 1
    print(f"Release evidence written to {json_path} and {markdown_path}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
