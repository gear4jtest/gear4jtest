import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
const dir = mkdtempSync(join(tmpdir(), 'gear4j-web-package-'));
try {
  const [packed] = JSON.parse(execFileSync('npm', ['pack', '--json', '--pack-destination', dir], { encoding: 'utf8' }));
  for (const file of ['src/index.mjs','src/management-client.mjs','src/studio-element.mjs','src/studio-workspace.mjs','src/studio-versions.mjs','src/studio-runs.mjs','src/studio-scenarios.mjs','src/studio-targets.mjs','src/studio-simulation.mjs','README.md','LICENSE','NOTICE'])
    assert.ok(packed.files.some(f => f.path === file), file);
  assert.ok(!packed.files.some(f => /node_modules|bootstrap|test\//.test(f.path)));
  writeFileSync(join(dir, 'package.json'), JSON.stringify({ private: true, type: 'module' }));
  execFileSync('npm', ['install', '--offline', '--ignore-scripts', '--no-audit', '--no-fund', '--package-lock=false', join(dir, packed.filename)], { cwd: dir, stdio: 'pipe' });
  writeFileSync(join(dir, 'consume.mjs'), `
    import { JSDOM } from ${JSON.stringify(import.meta.resolve('jsdom'))};
    const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
    for (const name of ['window','document','HTMLElement','customElements','CustomEvent']) globalThis[name] = dom.window[name];
    for (const spec of ['@gear4j/studio-web','@gear4j/studio-web/client','@gear4j/studio-web/editor','@gear4j/studio-web/workspace','@gear4j/studio-web/versions','@gear4j/studio-web/runs','@gear4j/studio-web/scenarios','@gear4j/studio-web/targets','@gear4j/studio-web/simulation']) await import(spec);
    for (const tag of ['gear4j-studio','gear4j-workspace','gear4j-versions','gear4j-runs','gear4j-scenarios','gear4j-targets','gear4j-simulation']) if (!customElements.get(tag)) throw new Error('Missing public component: ' + tag);
    dom.window.close();
  `);
  execFileSync(process.execPath, [join(dir, 'consume.mjs')], { cwd: dir, stdio: 'pipe' });
  console.log('Packed npm artifact installed and all public exports consumed successfully.');
} finally { rmSync(dir, { recursive: true, force: true }); }
