import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
for (const name of ['window', 'document', 'HTMLElement', 'customElements', 'CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-element.mjs');
const sample = { schemaVersion: 1, id: 'sample', inputType: 'java.lang.String', outputType: 'java.lang.String', nodes: [{ kind: 'operation', id: 'step', operationId: 'trim', parameters: {} }] };
const catalog = [{ id: 'trim', name: 'Trim', description: 'Synthetic', input: { javaType: 'java.lang.String' }, output: { javaType: 'java.lang.String' }, testAllowed: true, parameters: {} }];
function fixture(actions = ['READ', 'EDIT', 'VALIDATE', 'TEST']) {
 let number = 0; const calls = []; const element = document.createElement('gear4j-studio'); document.body.replaceChildren(element);
 const saved = definition => ({ draftId: 'draft', revisionId: `revision-${++number}`, number, definition: structuredClone(definition) });
 const client = {
  context: async () => ({ context: { id: 'test' }, actions, sample }), catalog: async () => catalog,
  create: async definition => { calls.push('create'); return saved(definition); },
  save: async (id, revision, definition) => { calls.push(['save', revision]); return saved(definition); },
  validate: async () => ({ valid: true, diagnostics: [] }), exportXml: async () => ({ source: '<assemblyLine />' }),
  test: async (id, request) => { calls.push(['test', request]); return { requestId: request.requestId, state: 'COMPLETED', run: { revision: request.revision, revisionId: `revision-${request.revision}`, outcome: 'SUCCEEDED', mode: 'TEST', runtimeContext: 'test', input: { state: 'NOT_CAPTURED', value: null }, output: { state: 'NOT_CAPTURED', value: null }, steps: [] } }; }
 };
 return { element, client, calls };
}
function button(element, text) { const found = [...element.shadowRoot.querySelectorAll('button')].find(b => b.textContent === text); assert.ok(found, text); return found; }
async function settled(element) { for (let i = 0; element.busy && i < 100; i++) await new Promise(resolve => setTimeout(resolve, 1)); assert.equal(element.busy, false); }
test('public component saves then tests the exact revision and emits host events', async () => {
 const { element, client, calls } = fixture(); const events = [];
 element.addEventListener('studio:revision', event => events.push(event.detail));
 await element.configure({ client }); assert.equal(button(element, 'Exécuter le test').disabled, true);
 button(element, 'Enregistrer le draft').click(); await settled(element);
 assert.deepEqual(events, [{ draftId: 'draft', revisionId: 'revision-1', number: 1 }]);
 assert.equal(button(element, 'Exécuter le test').disabled, false);
 button(element, 'Exécuter le test').click(); await settled(element);
 assert.equal(calls[1][0], 'test'); assert.equal(calls[1][1].revision, 1); assert.ok(calls[1][1].requestId);
 assert.match(element.shadowRoot.textContent, /SUCCEEDED/); assert.equal(element.pendingTest.input, undefined);
});
test('editing invalidates displayed validation and XML and disables stale tests', async () => {
 const { element, client } = fixture(); await element.configure({ client });
 for (const action of ['Enregistrer le draft','Exécuter le test','Valider','Générer le XML']) {button(element, action).click();await settled(element);}
 assert.match(element.shadowRoot.querySelector('[data-validation]').textContent, /Aucune incohérence/);
 assert.equal([...element.shadowRoot.querySelectorAll('input')].find(input => input.value === 'sample').disabled, true);
 element.definition.nodes.push({kind:'operation',id:'new',operationId:'trim',parameters:{}}); element.touch();
 assert.equal(button(element, 'Exécuter le test').disabled, true);
 assert.doesNotMatch(element.shadowRoot.querySelector('[data-validation]').textContent, /Aucune incohérence/);
 assert.equal(element.shadowRoot.querySelector('textarea.xml').value, ''); assert.equal(element.shadowRoot.querySelector('[data-run-stale]').hidden, false);
});
test('lost responses expose receipt recovery without submitting another test', async () => {
 const { element, client } = fixture(); let posts = 0; let recovery;
 client.test = async () => { posts++; throw new Error('network'); };
 client.testRequest = async id => { recovery = id; return { requestId: id, state: 'RUNNING', run: null }; };
 await element.configure({ client }); button(element, 'Enregistrer le draft').click(); await settled(element);
 button(element, 'Exécuter le test').click(); await settled(element); const requestId = element.pendingTest.requestId;
 button(element, 'Actualiser ce test').click(); await settled(element); assert.equal(posts, 1); assert.equal(recovery, requestId);
});
test('read-only mode disables mutations and labels are text, not HTML', async () => {
 const { element, client } = fixture(['READ']); client.catalog = async () => [{ ...catalog[0], name: '<img src=x onerror=alert(1)>' }];
 await element.configure({ client });
 assert.equal(button(element, 'Enregistrer le draft').disabled, true); assert.equal(button(element, 'Valider').disabled, true);
 assert.equal(element.shadowRoot.querySelector('img'), null); assert.match(element.shadowRoot.textContent, /<img src=x/);
});

test('XML preview is explicit, invalidates stale previews and never saves implicitly', async () => {
 const { element, client, calls } = fixture();
 client.previewXml = async source => {
   if (source.includes('unsupported')) throw { code: 'UNSUPPORTED_FORMAT' };
   return { definition: { ...structuredClone(sample), nodes: [] }, source: '<canonical />' };
 };
 await element.configure({ client });
 const original = structuredClone(element.definition);
 const edit = value => { const area = element.shadowRoot.querySelector('textarea.xml'); area.value = value; area.dispatchEvent(new dom.window.Event('input')); };
 edit('<original><!-- comment --></original>');
 assert.equal(element.hasUnsavedChanges, true);
 assert.equal(button(element, 'Enregistrer le draft').disabled, true);
 assert.equal(element.shadowRoot.querySelector('.grid').inert, true);
 button(element, 'Prévisualiser le XML').click(); await settled(element);
 assert.deepEqual(element.definition, original); assert.deepEqual(calls, []);
 edit('<unsupported />'); assert.equal(button(element, 'Appliquer au draft courant').disabled, true);
 button(element, 'Prévisualiser le XML').click(); await settled(element);
 assert.deepEqual(element.definition, original); assert.equal(element.xmlSource, '<unsupported />');
 edit('<original><!-- comment --></original>'); button(element, 'Prévisualiser le XML').click(); await settled(element);
 button(element, 'Appliquer au draft courant').click();
 assert.equal(element.definition.nodes.length, 0); assert.equal(element.dirty, true);
 assert.equal(element.xmlSource, '<canonical />'); assert.match(element.xmlOriginal, /comment/);
 assert.deepEqual(calls, []); assert.equal(element.shadowRoot.querySelector('.grid').inert, false);
});

test('an obsolete diagnostic response cannot replace analysis of newer local work', async () => {
 const { element, client } = fixture(); let deliver;
 client.diagnose = () => new Promise(resolve => { deliver = resolve; });
 await element.configure({ client }); element.touch();
 for (let i=0; !deliver && i<100; i++) await new Promise(resolve => setTimeout(resolve, 10));
 assert.ok(deliver); element.touch();
 deliver({ valid: false, diagnostics: [{code:'STALE', severity:'ERROR',path:'/',message:'obsolete'}] });
 await Promise.resolve(); await Promise.resolve();
 assert.equal(element.report, null); clearTimeout(element.diagnosticsTimer);
});
