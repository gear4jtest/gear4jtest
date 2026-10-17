import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ManagementClient } from '../src/management-client.mjs';
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost/' });
for (const name of ['window','document','HTMLElement','customElements','CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-versions.mjs');
const find = (element, text) => [...element.shadowRoot.querySelectorAll('button')].find(b => b.textContent === text);
async function settled(e) { for (let i = 0; e.busy && i < 200; i++) await new Promise(r => setTimeout(r, 1)); assert.equal(e.busy, false); }
const frozen = { version: { id: 'v1', chainId: 'chain', label: '1.0', revision: 2, source: '<script>alert(1)</script>', artifactHash: 'sha', runtimeAssemblyLineId: 'example', author: 'alice', createdAt: 'now' }, publications: [], actions: ['TEST', 'PUBLISH'] };
function fixture() {
 const calls = []; let state = structuredClone(frozen); let dirty = false; let kind = 'STRUCTURED';
 const client = {
  versions: async () => ({ items: [structuredClone(state)], nextCursor: null }), version: async () => structuredClone(state),
  createVersion: async (...args) => { calls.push(['freeze', ...args]); return structuredClone(state); },
  publishTest: async id => { calls.push(['publish', id]); state.publications.push({ mode: 'TEST', state: 'UNKNOWN', requestedBy: 'alice', requestedAt: 'now' }); return structuredClone(state); },
  refreshVersion: async id => { calls.push(['refresh', id]); state.publications[0].state = 'CONFIRMED'; state.actions.push('PROMOTE'); return structuredClone(state); },
  promoteRun: async id => { calls.push(['promote', id]); state.publications.push({ mode: 'RUN', state: 'CONFIRMED', publishedBy: 'reviewer', publishedAt: 'now' }); return structuredClone(state); },
  testVersion: async (id, request) => { calls.push(['test', id, request]); return { state: 'COMPLETED', run: { versionId: id, artifactHash: 'sha', output: { value: '<img src=x>' } } }; },
  testRequest: async id => ({ requestId: id, state: 'OUTCOME_UNKNOWN' })
 };
 const element = document.createElement('gear4j-versions'); document.body.replaceChildren(element);
 return { element, client, calls, getDraft: () => ({ dirty, revision: { kind, draftId: 'draft', number: 2 } }), dirty: value => { dirty = value; }, source: () => { kind = 'SOURCE'; } };
}
test('lifecycle client transmits only the exact version identity and explicit operation', async () => {
 const calls = []; const client = new ManagementClient({ fetchImpl: async (url, init) => { calls.push([url, init.method, JSON.parse(init.body || '{}')]); return { ok: true, json: async () => ({}) }; } });
 await client.createVersion('draft', 2, '1.0'); await client.publishTest('v1'); await client.promoteRun('v1'); await client.refreshVersion('v1'); await client.testVersion('v1', { requestId: 'request', input: 'example' });
 assert.deepEqual(calls, [['/api/v1/drafts/draft/versions','POST',{ revision: 2, label: '1.0' }],['/api/v1/versions/v1/publish','POST',{}],['/api/v1/versions/v1/promote','POST',{}],['/api/v1/versions/v1/refresh','POST',{}],['/api/v1/versions/v1/tests','POST',{ requestId: 'request', input: 'example' }]]);
});
test('frozen versions use distinct publication and promotion actions with explicit uncertain-outcome refresh', async () => {
 const f = fixture(); const e = f.element; await e.configure({ client: f.client, chainId: 'chain', getDraft: f.getDraft, canFreeze: true });
 const label = e.shadowRoot.querySelector('input'); label.value = '1.0'; label.dispatchEvent(new dom.window.Event('input'));
 f.dirty(true); find(e, 'Figer une version').click(); await settled(e); assert.deepEqual(f.calls, []); assert.match(e.shadowRoot.textContent, /Enregistrez une révision structurée/);
 f.dirty(false); find(e, 'Figer une version').click(); await settled(e); assert.deepEqual(f.calls, [['freeze','draft',2,'1.0']]);
 assert.equal(find(e, 'Promouvoir en RUN'), undefined); assert.equal(e.shadowRoot.querySelector('script'), null);
 find(e, 'Publier en TEST').click(); await settled(e); assert.match(e.shadowRoot.textContent, /UNKNOWN/); assert.equal(f.calls.length, 2);
 find(e, 'Actualiser les publications').click(); await settled(e); assert.equal(find(e, 'Publier en TEST').disabled, true);
 find(e, 'Promouvoir en RUN').click(); await settled(e); assert.equal(find(e, 'Promouvoir en RUN').disabled, true);
 find(e, 'Tester cette version').click(); await settled(e); assert.equal(f.calls.at(-1)[0], 'test'); assert.equal(f.calls.at(-1)[1], 'v1');
 assert.match(e.shadowRoot.textContent, /versionId/); assert.equal(e.shadowRoot.querySelector('img'), null);
 f.source(); e.render(); assert.equal(find(e, 'Figer une version').disabled, true);
});
test('read-only consumers cannot publish and an interrupted HTTP request is never retried automatically', async () => {
 const f = fixture(); const e = f.element; await e.configure({ client: f.client, chainId: 'chain' }); e.choose({ ...frozen, actions: [] });
 assert.equal(find(e, 'Publier en TEST'), undefined); assert.equal(find(e, 'Tester cette version'), undefined);
 e.choose(frozen); let calls = 0; f.client.publishTest = async () => { calls++; throw new Error('synthetic transport failure'); };
 find(e, 'Publier en TEST').click(); await settled(e); assert.equal(calls, 1); assert.match(e.shadowRoot.textContent, /Actualisez l’état/); assert.equal(e.selected.version.id, 'v1');
});
