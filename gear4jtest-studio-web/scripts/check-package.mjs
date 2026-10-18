import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
const dir = mkdtempSync(join(tmpdir(), 'gear4j-web-package-'));
try {
  const [packed] = JSON.parse(execFileSync('npm', ['pack', '--json', '--pack-destination', dir], { encoding: 'utf8' }));
  for (const file of ['src/index.mjs','src/management-client.mjs','src/studio-element.mjs','src/studio-workspace.mjs','src/studio-versions.mjs','src/studio-runs.mjs','src/studio-scenarios.mjs','src/studio-targets.mjs','src/studio-simulation.mjs','README.md'])
    assert.ok(packed.files.some(f => f.path === file), file);
  assert.ok(!packed.files.some(f => /node_modules|bootstrap|test\//.test(f.path)));
  execFileSync('tar', ['-xzf', join(dir, packed.filename), '-C', dir]);
  execFileSync(process.execPath, ['--input-type=module', '-e', `import { ManagementClient } from '${join(dir, 'package/src/management-client.mjs')}'; if (!ManagementClient) process.exit(1);`]);
  const manifest = JSON.parse(readFileSync(join(dir, 'package/package.json')));
  assert.equal(manifest.name, '@gear4j/studio-web');
  console.log('Packed public package consumed successfully; no demo or runtime dependency.');
} finally { rmSync(dir, { recursive: true, force: true }); }
