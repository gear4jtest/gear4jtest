"""Tests of the evidence gate use synthetic reports only inside temporary directories."""
import json
import tempfile
import unittest
from pathlib import Path

from verify_studio_qualification import AUDIT_RESULT, BROWSER_RESULT, POSTGRES_RESULT, collect


class StudioQualificationTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.xml = '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="populatedBaselineRestoreChecksumsContextAndConcurrentRestart()"/></testsuite>'
        self.browser = {'status': 'PASS', 'browser': 'fixture', 'checks': [p + ': fixture' for p in ('java', 'spring', 'java_embedded', 'spring_embedded', 'standalone')]}
        self.audit = {'auditReportVersion': 2, 'metadata': {'vulnerabilities': {'high': 0, 'critical': 0, 'total': 0}}}
        self.write(POSTGRES_RESULT, self.xml)
        self.write(BROWSER_RESULT, json.dumps(self.browser))
        self.write(AUDIT_RESULT, json.dumps(self.audit))

    def write(self, path, text):
        path = self.root / path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def test_binds_identity_and_report_hashes(self):
        result = collect(self.root, '1.0.0-rc1', 'a' * 40)
        self.assertEqual('a' * 40, result['commitSha'])
        self.assertEqual('1.0.0-rc1', result['projectVersion'])
        self.assertEqual(3, len(result['evidence']))
        self.assertTrue(all(len(p['sha256']) == 64 for p in result['evidence']))
        self.assertFalse(result['officialPublication'])

    def test_rejects_failed_skipped_empty_or_wrong_postgres_test(self):
        for xml in (self.xml.replace('failures="0"', 'failures="1"'),
                    self.xml.replace('skipped="0"', 'skipped="1"'),
                    self.xml.replace('tests="1"', 'tests="0"'),
                    self.xml.replace('ConcurrentRestart()', 'DifferentTest()'),
                    self.xml.replace('/></testsuite>', '><skipped/></testcase></testsuite>')):
            with self.subTest(xml=xml):
                self.write(POSTGRES_RESULT, xml)
                with self.assertRaises(ValueError):
                    collect(self.root, 'test')

    def test_rejects_missing_postgres_report(self):
        (self.root / POSTGRES_RESULT).unlink()
        with self.assertRaises(OSError):
            collect(self.root, 'test')

    def test_rejects_missing_browser_journey(self):
        self.browser['checks'].pop()
        self.write(BROWSER_RESULT, json.dumps(self.browser))
        with self.assertRaises(ValueError):
            collect(self.root, 'test')

    def test_rejects_incomplete_failed_or_vulnerable_audit(self):
        for report in ({}, {'error': {'code': 'ENETWORK'}},
                       {'auditReportVersion': 2, 'metadata': {'vulnerabilities': {'high': 1, 'critical': 0, 'total': 1}}}):
            with self.subTest(report=report):
                self.write(AUDIT_RESULT, json.dumps(report))
                with self.assertRaises(ValueError):
                    collect(self.root, 'test')


if __name__ == '__main__':
    unittest.main()
