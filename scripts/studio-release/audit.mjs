// Qualification fails closed when the registry cannot supply a complete audit.
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
const root = fileURLToPath(new URL('../../', import.meta.url));
let output;
let status = 0;
try {
  output = execFileSync(process.platform === 'win32' ? 'npm.cmd' : 'npm',
    ['audit', '--json', '--audit-level=high'], {
      cwd: resolve(root, 'gear4jtest-studio-web'), encoding: 'utf8',
      timeout: 120000, maxBuffer: 8 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe']
    });
} catch (error) {
  status = error.status || 1;
  output = error.stdout?.toString() || JSON.stringify({ error: { code: 'AUDIT_UNAVAILABLE' } });
}
const directory = resolve(root, 'build/reports/studio-release');
mkdirSync(directory, { recursive: true });
writeFileSync(resolve(directory, 'npm-audit.json'), output);
try {
  const report = JSON.parse(output);
  const counts = report.metadata?.vulnerabilities;
  if (report.auditReportVersion !== 2 || report.error || !counts ||
      ['high', 'critical', 'total'].some(k => !Number.isInteger(counts[k]) || counts[k] < 0))
    throw new Error('Incomplete audit');
  console.log(`npm audit: ${counts.total} findings (${counts.high} high, ${counts.critical} critical)`);
  process.exitCode = status || (counts.high + counts.critical > 0 ? 1 : 0);
} catch {
  console.error('npm audit did not return a complete report; qualification rejected.');
  process.exitCode = 1;
}
