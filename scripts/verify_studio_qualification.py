#!/usr/bin/env python3
"""Reject incomplete Studio qualification; record only actual successful evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

POSTGRES_RESULT = Path('gear4jtest-studio-jdbc/build/test-results/studioPostgresQualification/TEST-io.github.gear4jtest.studio.jdbc.RequiredPostgreSqlStudioIT.xml')
BROWSER_RESULT = Path('build/reports/studio-release-browser/result.json')
AUDIT_RESULT = Path('build/reports/studio-release/npm-audit.json')


def collect(root: Path, version: str, commit: str = '') -> dict:
    suite = ET.parse(root / POSTGRES_RESULT).getroot()
    cases = suite.findall('testcase')
    if (suite.tag != 'testsuite' or suite.get('tests') != '1'
            or any(suite.get(key) != '0' for key in ('failures', 'errors', 'skipped'))
            or len(cases) != 1
            or cases[0].get('name') != 'populatedBaselineRestoreChecksumsContextAndConcurrentRestart()'
            or any(cases[0].find(tag) is not None for tag in ('failure', 'error', 'skipped'))):
        raise ValueError('PostgreSQL qualification must execute and pass exactly the required test, with no skips')
    browser = json.loads((root / BROWSER_RESULT).read_text())
    checks = browser.get('checks', [])
    if (browser.get('status') != 'PASS' or not browser.get('browser')
            or not isinstance(checks, list)
            or not all(any(isinstance(c, str) and c.startswith(prefix + ':') for c in checks)
                       for prefix in ('java', 'spring', 'java_embedded', 'spring_embedded', 'standalone'))):
        raise ValueError('All five real browser journeys must pass')
    audit = json.loads((root / AUDIT_RESULT).read_text())
    counts = audit.get('metadata', {}).get('vulnerabilities', {})
    if (audit.get('auditReportVersion') != 2 or audit.get('error')
            or any(type(counts.get(k)) is not int or counts[k] < 0 for k in ('high', 'critical', 'total'))
            or counts['high'] or counts['critical']):
        raise ValueError('An actual complete npm audit with no high/critical findings is required')
    if commit and not re.fullmatch('[0-9a-f]{40}', commit):
        raise ValueError('Invalid commit SHA')
    return {
        'schemaVersion': 1, 'status': 'PASS', 'projectVersion': version,
        'commitSha': commit or None,
        'generatedAt': datetime.now(timezone.utc).isoformat(),
        'postgresql': {'majorVersion': 17, 'tests': 1, 'failures': 0, 'skipped': 0},
        'browser': browser, 'npmVulnerabilities': counts,
        'evidence': [{'path': p.as_posix(), 'sha256': hashlib.sha256((root / p).read_bytes()).hexdigest()}
                     for p in (POSTGRES_RESULT, BROWSER_RESULT, AUDIT_RESULT)],
        'officialPublication': False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path.cwd())
    parser.add_argument('--project-version', required=True)
    args = parser.parse_args()
    output = args.root / 'build/reports/studio-release/qualification.json'
    output.unlink(missing_ok=True)
    try:
        result = collect(args.root, args.project_version, os.environ.get('GITHUB_SHA', ''))
    except (OSError, ValueError, ET.ParseError) as error:
        raise SystemExit(f'Studio qualification rejected: {error}') from error
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print('Studio qualification PASS; evidence: ' + str(output))


if __name__ == '__main__':
    main()
