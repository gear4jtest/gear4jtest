import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ManagementClient, ScopedAuthoringClient } from '../src/management-client.mjs';
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
for (const name of ['window', 'document', 'HTMLElement', 'customElements', 'CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-workspace.mjs');
const definition = { schemaVersion: 1, id: 'sample', inputType: 'java.lang.String', outputType: 'java.lang.String', nodes: [] };
const permissions = ['READ','EDIT','TEST','VALIDATE','MANAGE_ACCESS','READ_AUDIT','ARCHIVE'];
const state = { draft: { id: 'draft', chainId: 'chain', name: 'Mon draft', owner: 'alice', version: 7 }, permissions, revision: { draftId: 'draft', revisionId: 'revision', number: 2, definition } };
const info = { identity: { principalId: 'alice' }, context: { id: 'local', capabilities: ['VALIDATE','CONTROLLED_TEST'] }, canCreateDomain: true, permissions, sample: definition };
const find = (root, selector, text) => [...root.querySelectorAll(selector)].find(e => e.textContent === text);
async function settled(element) { for (let i = 0; element.busy && i < 200; i++) await new Promise(r => setTimeout(r, 1)); assert.equal(element.busy, false); }
function fixture() {
 const client = { context: async () => info, list: async () => ({ items: [], nextCursor: null }), draft: async () => structuredClone(state), catalog: async () => [], resource: async () => ({ resource: { id: 'chain', name: 'Chain', version: 1, owner: 'alice' }, kind: 'CHAIN', permissions: ['READ','CREATE_DRAFT'] }) };
 const element = document.createElement('gear4j-workspace'); document.body.replaceChildren(element); return { element, client };
}
test('P1 client sends optimistic resource version on versioned routes with host authentication', async () => {
 const requests = []; const client = new ManagementClient({ tokenProvider: () => 'host', fetchImpl: async (url, init) => { requests.push({ url, init }); return { ok: true, json: async () => ({}) }; } });
 await client.save('draft', 7, definition); await client.list('drafts', { parent: 'chain', after: 'cursor', limit: 10 });
 assert.equal(requests[0].url, '/api/v1/drafts/draft'); assert.equal(requests[0].init.headers.Authorization, 'Bearer host');
 assert.deepEqual(JSON.parse(requests[0].init.body), { expectedVersion: 7, definition });
 assert.equal(requests[1].url, '/api/v1/drafts?limit=10&parent=chain&after=cursor');
});
test('scoped editor uses resource version for writes and immutable revision for a controlled test', async () => {
 const calls = []; const client = { resource: async () => ({ permissions: ['READ'] }), catalog: async () => [], save: async (id, version, value) => { calls.push(['save', id, version]); return { ...state, draft: { ...state.draft, version: 8 }, revision: { ...state.revision, number: 3, definition: value } }; }, test: async (id, request) => { calls.push(['test', id, request.revision]); return { state: 'RUNNING', run: null }; } };
 const scoped = new ScopedAuthoringClient(client, state, info); const editor = document.createElement('gear4j-studio');
 await editor.configure({ client: scoped, revision: scoped.flat(state) });
 find(editor.shadowRoot, 'button', 'Enregistrer le draft').click(); await settled(editor);
 find(editor.shadowRoot, 'button', 'Exécuter le test').click(); await settled(editor);
 assert.deepEqual(calls, [['save','draft',7],['test','draft',3]]); assert.ok(find(editor.shadowRoot, 'button', 'Prévisualiser le XML'));
 const unavailable = new ScopedAuthoringClient(client, state, { ...info, context: { ...info.context, capabilities: [] } });
 await editor.configure({ client: unavailable, revision: unavailable.flat(state) });
 assert.equal(find(editor.shadowRoot, 'button', 'Exécuter le test').disabled, true);
 assert.equal(find(editor.shadowRoot, 'button', 'Enregistrer le draft').disabled, false);
});
test('workspace preserves an unsaved draft until explicit discard and survives failed navigation', async () => {
 const { element, client } = fixture(); await element.configure({ client }); await element.choose({ id: 'draft', kind: 'DRAFT' });
 const editor = element.editor; editor.definition.id = 'unsaved'; editor.touch();
 await element.choose({ id: 'chain', kind: 'CHAIN' }); assert.equal(element.editor, editor); assert.equal(element.pending.id, 'chain');
 find(element.shadowRoot, 'button', 'Rester sur le draft').click(); assert.equal(editor.definition.id, 'unsaved');
 client.resource = async () => { throw Object.assign(new Error(), { code: 'NOT_FOUND' }); };
 await element.perform(() => element.choose({ id: 'missing', kind: 'CHAIN' }, true));
 assert.equal(element.editor, editor); assert.equal(editor.definition.id, 'unsaved');
 client.resource = async () => ({ resource: { id: 'chain', name: 'Chain', version: 1 }, kind: 'CHAIN', permissions: ['READ'] });
 await element.choose({ id: 'chain', kind: 'CHAIN' }, true); assert.equal(element.editor, null); assert.equal(element.selected.resource.id, 'chain');
});
test('ACL screen sends the policy version and grants but never supplies the acting identity', async () => {
 const { element, client } = fixture(); let observed;
 client.access = async () => ({ version: 7, policy: { inheritAccess: false, grants: [] }, permissions, fullAdministration: true, delegablePermissions: permissions });
 client.setAccess = async (...args) => { observed = args; return { resource: { ...state.draft, version: 8 }, kind: 'DRAFT', permissions }; };
 await element.configure({ client }); await element.choose({ id: 'draft', kind: 'DRAFT' });
 find(element.shadowRoot, 'button', 'Gérer les accès').click(); await settled(element);
 find(element.shadowRoot, 'button', 'Ajouter un bénéficiaire').click();
 assert.equal(element.hasUnsavedChanges, true);
 const input = element.shadowRoot.querySelector('.grant input:not([type=checkbox])'); input.value = 'bob'; input.dispatchEvent(new dom.window.Event('input'));
 find(element.shadowRoot, 'button', 'Enregistrer les accès').click(); await settled(element);
 assert.deepEqual(observed, ['draft', 7, { inheritAccess: false, grants: [{ subjectType: 'USER', subject: 'bob', permissions: ['READ'] }] }]);
 assert.equal(element.hasUnsavedChanges, false);
});
test('creation form retains entered values after a conflict and discovery does not permit opening', async () => {
 const { element, client } = fixture(); client.list = async () => ({ items: [{ id: 'hidden', name: '<script>private</script>', kind: 'DOMAIN', readable: false }], nextCursor: null });
 client.createDomain = async () => { throw Object.assign(new Error(), { code: 'CONFLICT' }); }; await element.configure({ client });
 const form = element.shadowRoot.querySelector('form'); const inputs = form.querySelectorAll('input');
 inputs[0].value = 'catalog'; inputs[1].value = 'Catalogue'; inputs.forEach(i => i.dispatchEvent(new dom.window.Event('input')));
 form.dispatchEvent(new dom.window.Event('submit', { cancelable: true })); await settled(element);
 assert.deepEqual([...element.shadowRoot.querySelector('form').querySelectorAll('input')].map(i => i.value), ['catalog','Catalogue']);
 assert.equal(element.shadowRoot.querySelector('.list button').disabled, true); assert.equal(element.shadowRoot.querySelector('script'), null);
 assert.match(element.shadowRoot.textContent, /Conflit/);
});

test('denied refresh removes the selected definition and related cached data after revocation', async () => {
 const { element, client } = fixture(); await element.configure({ client }); await element.choose({ id: 'draft', kind: 'DRAFT' });
 element.history = { items: [state.revision] }; element.comparison = { previous: definition };
 element.formValues.set('metadata:draft', { name: 'private' });
 client.draft = async () => { throw Object.assign(new Error(), { code: 'NOT_FOUND' }); };
 await element.perform(() => element.choose({ id: 'draft', kind: 'DRAFT' }));
 assert.equal(element.selected, null); assert.equal(element.editor, null);
 assert.equal(element.history, null); assert.equal(element.comparison, null);
 assert.equal(element.formValues.size, 0); assert.equal(element.shadowRoot.querySelector('gear4j-studio'), null);
 assert.match(element.message, /inaccessible/);
});
