/** Real-browser release gate against packaged launchers. No external services or persistent credentials. */
import { chromium } from '../../gear4jtest-studio-web/node_modules/playwright/index.mjs';
import { expect as baseExpect } from '../../gear4jtest-studio-web/node_modules/playwright/test.mjs';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { mkdir, mkdtemp, writeFile, readFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
const root = resolve(import.meta.dirname, '../..');
const report = resolve(root, 'build/reports/studio-release-browser');
await mkdir(report, { recursive: true });
const work = await mkdtemp(resolve(report, 'session-'));
const children = [], checks = [], errors = [];
const editorToken = randomUUID(), viewerToken = randomUUID(), remoteToken = randomUUID();
let browser, page, outcome, pendingError;
const expect = baseExpect.configure({ timeout: 12000 });
async function port() { const server = createServer(); await new Promise(r => server.listen(0, '127.0.0.1', r)); const p = server.address().port; await new Promise(r => server.close(r)); return p; }
async function launch(module, args, environment) {
  const log = resolve(work, `${module}.log`), stream = createWriteStream(log);
  const proc = spawn(resolve(root, module, 'build/install', module, 'bin', module), args, { cwd: work, env: { ...process.env, ...environment }, stdio: ['ignore', 'pipe', 'pipe'] });
  proc.stdout.pipe(stream); proc.stderr.pipe(stream); children.push({ proc, log, stream }); return proc;
}
async function ready(base, proc) {
  for (let i = 0; i < 150; i++) {
    if (proc.exitCode !== null) throw new Error(`Packaged launcher exited: ${proc.exitCode}`);
    try { if ((await fetch(base, { signal: AbortSignal.timeout(500) })).ok) return; } catch { /* Starting. */ }
    await new Promise(r => setTimeout(r, 200));
  } throw new Error('Packaged application did not start');
}
async function api(base, path, token = remoteToken, method = 'GET', body, extra = {}) {
  return fetch(`${base}/api/v1${path}`, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}), ...extra }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) });
}
async function login(p, url, token, federated = false) {
  await p.goto(url); await p.getByLabel(federated ? 'Token d’accès au Studio' : 'Token d’accès local').fill(token);
  const submit = p.getByRole('button', { name: federated ? 'Se connecter' : 'Ouvrir le Studio', exact: true });
  await submit.focus(); await expect(submit).toBeFocused(); await p.keyboard.press('Enter');
  if (federated) await p.getByLabel('Application / cible').selectOption('java');
  await expect(p.getByRole('heading', { name: 'Espace de travail', exact: true })).toBeVisible();
}
async function openDraft(p, id) {
  await p.getByText('Ouvrir un draft par son identifiant', { exact: true }).click();
  await p.getByLabel('Identifiant du draft', { exact: true }).fill(id);
  await p.getByRole('button', { name: 'Ouvrir le draft', exact: true }).click();
  await expect(p.getByRole('heading', { name: 'Composer une chaîne', exact: true })).toBeVisible();
}
async function journey(label, url, base, token, federated = false) {
  console.log(`Browser: ${label}`);
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.on('pageerror', e => errors.push(String(e)));
  await login(page, url, token, federated);
  for (const [kind, title] of [['domaine', 'domaine'], ['chaîne', 'chaîne'], ['draft', 'draft']]) {
    // Details scope avoids the identically named submit button.
    const details = page.locator('details').filter({ has: page.locator('summary').filter({ hasText: new RegExp(`^Créer ${kind === 'chaîne' ? 'une' : 'un'} ${title}$`) }) });
    await details.locator('summary').click();
    if (kind !== 'draft') await details.getByLabel('Clé (minuscules et tirets)').fill(`rr-${label.toLowerCase().replaceAll('_', '-')}`);
    await details.getByLabel('Nom', { exact: true }).fill(`${label} ${kind}`);
    await details.getByRole('button', { name: `Créer ${kind === 'chaîne' ? 'une' : 'un'} ${title}`, exact: true }).click();
  }
  const component = page.locator('gear4j-studio');
  await expect(page.getByRole('heading', { name: 'Composer une chaîne', exact: true })).toBeVisible();
  const id = await component.evaluate(e => e.revision.draftId);
  await component.getByRole('button', { name: 'Valider', exact: true }).click();
  await expect(component.getByText('Aucune incohérence détectée pour ce catalogue.', { exact: true })).toBeVisible();
  await component.getByRole('button', { name: 'Générer le XML', exact: true }).click();
  const xml = component.getByLabel('Définition XML', { exact: true });
  await expect(xml).toHaveValue(/assemblyLine/); const original = await xml.inputValue(), invalid = original.replace('</assemblyLine>', '');
  await xml.fill(invalid);
  await Promise.all([page.waitForResponse(r => r.url().endsWith('/source') && r.status() === 200), component.getByRole('button', { name: 'Enregistrer le draft', exact: true }).click()]);
  await expect.poll(() => component.evaluate(e => e.revision.kind)).toBe('SOURCE');
  await expect(component.getByRole('button', { name: 'Exécuter le test', exact: true })).toHaveCount(0);
  await login(page, url, token, federated); await openDraft(page, id); await expect(xml).toHaveValue(invalid);
  await xml.fill(original); await component.getByRole('button', { name: 'Prévisualiser le XML', exact: true }).click();
  await component.getByRole('button', { name: 'Appliquer au draft courant', exact: true }).click();
  await component.getByRole('button', { name: 'Enregistrer le draft', exact: true }).click();
  await component.getByRole('button', { name: 'Exécuter le test', exact: true }).click();
  await expect(component.getByText('SUCCEEDED', { exact: true }).first()).toBeVisible();
  const versions = page.locator('gear4j-versions');
  await versions.getByLabel('Libellé immuable (ex. 1.0.0)').fill('1.0.0');
  await versions.getByRole('button', { name: 'Figer une version', exact: true }).click();
  await versions.getByRole('button', { name: 'Publier en TEST', exact: true }).click();
  await expect(versions.getByText('CONFIRMED', { exact: true })).toHaveCount(1);
  await versions.getByRole('button', { name: 'Promouvoir en RUN', exact: true }).click();
  await expect(versions.getByText('CONFIRMED', { exact: true })).toHaveCount(2);
  const scenarios = page.locator('gear4j-scenarios');
  await scenarios.getByLabel('Nom du scénario', { exact: true }).fill('Synthetic release scenario');
  await scenarios.getByLabel('Entrée synthétique exacte', { exact: true }).fill('  hello  ');
  await scenarios.getByRole('button', { name: 'Préparer les mocks des nœuds', exact: true }).click();
  const behaviours = scenarios.getByRole('combobox', { name: /^Comportement/ });
  await expect(behaviours).toHaveCount(2);
  for (let i = 0; i < 2; i++) { await behaviours.nth(i).selectOption('PASSTHROUGH'); await expect(behaviours.nth(i)).toHaveValue('PASSTHROUGH'); }
  await scenarios.getByRole('button', { name: 'Enregistrer le scénario', exact: true }).click();
  await expect(scenarios.getByText('Scénario enregistré · révision 1.', { exact: true })).toBeVisible();
  for (let i = 0; i < 2; i++) await expect(behaviours.nth(i)).toHaveValue('PASSTHROUGH');
  await scenarios.getByRole('button', { name: 'Simuler les substitutions', exact: true }).click();
  await scenarios.getByRole('button', { name: 'Relire le rapport', exact: true }).click();
  await expect(scenarios.getByText('Rapport relu sans nouvelle simulation.', { exact: true })).toBeVisible();
  await expect(scenarios.getByText(/^Résultat simulé : SUCCEEDED/)).toBeVisible();
  await page.screenshot({ path: resolve(report, `${label}-desktop.png`) });
  await page.setViewportSize({ width: 390, height: 844 });
  const geometry = await page.evaluate(() => {
    const overflowing = [];
    function visit(root) { for (const e of root.querySelectorAll('*')) {
      const r = e.getBoundingClientRect(); if (r.width > 0 && r.right > innerWidth + 0.1) overflowing.push({ tag: e.tagName, className: String(e.className), left: r.left, right: r.right, width: r.width });
      if (e.shadowRoot) visit(e.shadowRoot);
    } } visit(document);
    return { viewport: innerWidth, width: document.documentElement.scrollWidth, overflowing };
  });
  assert.ok(geometry.width <= geometry.viewport, `${label}: mobile overflow ${JSON.stringify(geometry)}`);
  await page.screenshot({ path: resolve(report, `${label}-mobile.png`), fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  assert.deepEqual(await page.evaluate(() => [localStorage.length, sessionStorage.length]), [0, 0]);
  assert.equal((await api(base, `/drafts/${id}`, viewerToken)).status, 404);
  const query = new URLSearchParams({ from: new Date(Date.now() - 300000).toISOString(), until: new Date(Date.now() + 60000).toISOString() });
  const history = await (await api(base, `/resources/${id}/runs?${query}`, token)).json(); assert.equal(history.items.length, 1, 'Simulation must not create a runtime run');
  if (!federated) for (let i = 0; i < 2; i++) { const sse = await api(base, `/runs/${history.items[0].id}/events`, token); assert.equal(sse.status, 200); assert.match(await sse.text(), /event:\s*snapshot/); }
  if (label === 'java') await revokeAccess(base, url, id);
  if (federated) { await page.getByLabel('Application / cible').selectOption('spring'); await expect(page.locator('gear4j-studio')).toHaveCount(0); }
  checks.push(`${label}: authoring, SOURCE reload, controlled test, TEST/RUN, scenario/simulation, mobile, memory-only credentials`);
  await page.close(); page = null;
}
async function revokeAccess(base, url, id) {
  const resource = await (await api(base, `/resources/${id}`)).json();
  const grant = await api(base, `/resources/${id}/access`, remoteToken, 'PUT', { expectedVersion: resource.resource.version, policy: { inheritAccess: false, grants: [{ subjectType: 'USER', subject: 'demo-viewer', permissions: ['READ'] }] } });
  assert.equal(grant.status, 200); const changed = await grant.json();
  const viewer = await browser.newPage(); await login(viewer, url, viewerToken); await openDraft(viewer, id);
  const revoked = await api(base, `/resources/${id}/access`, remoteToken, 'PUT', { expectedVersion: changed.resource.version, policy: { inheritAccess: false, grants: [] } }); assert.equal(revoked.status, 200);
  await viewer.getByRole('button', { name: 'Actualiser', exact: true }).click();
  await expect(viewer.getByText('Ressource introuvable ou inaccessible.', { exact: true })).toBeVisible();
  await expect(viewer.locator('gear4j-studio')).toHaveCount(0);
  await viewer.close(); checks.push('ACL revocation clears previously displayed resource');
}
try {
  const jp = await port(), sp = await port(), fp = await port();
  const java = `http://127.0.0.1:${jp}`, spring = `http://127.0.0.1:${sp}`, standalone = `http://127.0.0.1:${fp}`;
  for (const [module, p, name, url] of [['gear4jtest-studio-demo', jp, 'java', java], ['gear4jtest-studio-spring-demo', sp, 'spring', spring]]) {
    await ready(url, await launch(module, [String(p)], { GEAR4J_STUDIO_TOKEN: remoteToken, GEAR4J_STUDIO_VIEWER_TOKEN: viewerToken, GEAR4J_STUDIO_CAPTURE: 'synthetic', GEAR4J_STUDIO_DB_FILE: resolve(work, name) }));
  }
  const config = resolve(work, 'standalone.properties');
  await writeFile(config, `port=${fp}\napplications=examples\napplication.examples.name=Examples\ntargets=java,spring\ntarget.java.application=examples\ntarget.java.name=Java\ntarget.java.context=studio-local.java\ntarget.java.endpoint=${java}\ntarget.spring.application=examples\ntarget.spring.name=Spring\ntarget.spring.context=studio-local.spring\ntarget.spring.endpoint=${spring}\nusers=editor,viewer\nuser.editor.principalId=demo-editor\nuser.editor.tokenEnvironment=STUDIO_LOGIN\nuser.editor.targets=java,spring\nuser.editor.remoteTokenEnvironment.java=REMOTE_LOGIN\nuser.editor.remoteTokenEnvironment.spring=REMOTE_LOGIN\nuser.viewer.principalId=demo-viewer\nuser.viewer.tokenEnvironment=VIEWER_LOGIN\nuser.viewer.targets=java\nuser.viewer.remoteTokenEnvironment.java=VIEWER_LOGIN\n`);
  await ready(standalone, await launch('gear4jtest-studio-standalone', [config], { STUDIO_LOGIN: editorToken, REMOTE_LOGIN: remoteToken, VIEWER_LOGIN: viewerToken }));
  for (const base of [java, spring]) {
    assert.equal((await api(base, '/context', '')).status, 401); assert.equal((await api(base, '/context', 'invalid')).status, 401);
    assert.equal((await api(base, '/context', remoteToken, 'GET', undefined, { Origin: 'https://untrusted.example' })).status, 403);
    assert.equal((await api(base, '/context', remoteToken, 'GET', undefined, { 'X-Gear4J-Expected-Context': 'wrong' })).status, 409);
    const response = await api(base, '/context'); assert.equal(response.headers.get('cache-control'), 'no-store'); assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
    assert.equal((await api(base, '/domains', viewerToken, 'POST', { key: 'forbidden', name: 'Forbidden', description: '' })).status, 403);
  }
  const inventory = await fetch(`${standalone}/studio/api/v1/targets`, { headers: { Authorization: `Bearer ${editorToken}` } }); const inventoryText = await inventory.text();
  for (const secret of [java, spring, remoteToken, editorToken]) assert.equal(inventoryText.includes(secret), false);
  assert.equal((await fetch(`${standalone}/studio/api/v1/targets`)).status, 401);
  assert.equal((await api(`${standalone}/targets/spring`, '/context', viewerToken)).status, 404);
  assert.equal((await api(`${standalone}/targets/java`, '/arbitrary-route', editorToken)).status, 400);
  checks.push('Packaged HTTP authentication, authorization, origin, expected-context, target isolation and inventory redaction');
  browser = await chromium.launch({ headless: true, ...(process.env.STUDIO_BROWSER_EXECUTABLE ? { executablePath: process.env.STUDIO_BROWSER_EXECUTABLE } : {}) });
  for (const [label, url, base, token, federated] of [['java', java, java, remoteToken, false], ['spring', spring, spring, remoteToken, false], ['java_embedded', `${java}/embedded`, java, remoteToken, false], ['spring_embedded', `${spring}/embedded`, spring, remoteToken, false], ['standalone', standalone, `${standalone}/targets/java`, editorToken, true]]) await journey(label, url, base, token, federated);
  assert.deepEqual(errors, []);
  outcome = { status: 'PASS', browser: browser.version(), checks };
} catch (error) {
  if (page) await page.screenshot({ path: resolve(report, 'failure.png'), fullPage: true }).catch(() => {});
  outcome = { status: 'FAIL', checks, error: String(error), pageErrors: errors }; pendingError = error;
} finally {
  try {
  if (browser) await browser.close();
  for (const { proc } of children) if (proc.exitCode === null) proc.kill('SIGTERM');
  await Promise.all(children.map(async ({ proc, stream, log }) => {
    for (let i = 0; proc.exitCode === null && i < 50; i++) await new Promise(r => setTimeout(r, 100));
    if (proc.exitCode === null) proc.kill('SIGKILL'); stream.end();
    const content = await readFile(log, 'utf8'); for (const token of [editorToken, viewerToken, remoteToken]) assert.equal(content.includes(token), false, 'Credential leaked in launcher logs');
  }));
  checks.push('Launcher logs checked for credential leakage; all child processes stopped');
  } catch (error) {
    outcome = { status: 'FAIL', checks, error: String(error) }; pendingError = error;
  }
  await writeFile(resolve(report, 'result.json'), JSON.stringify(outcome, null, 2));
}
if (pendingError) throw pendingError;
