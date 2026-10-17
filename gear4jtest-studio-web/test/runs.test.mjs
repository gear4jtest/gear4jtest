import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ManagementClient } from '../src/management-client.mjs';
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost/' });
for (const name of ['window','document','HTMLElement','Node','customElements','CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-runs.mjs');
const run = { id: 'observation', status: 'SUCCEEDED', origin: 'CONTROLLED_TEST', createdAt: '2026-10-04T10:00:00Z', updatedAt: '2026-10-04T10:00:01Z', startedAt: '2026-10-04T10:00:00Z', endedAt: '2026-10-04T10:00:01Z', provenance: { mode: 'TEST', revisionId: 'revision', revision: 2, artifactHash: 'hash' }, observation: { runId: 'engine-id', input: { state: 'REDACTED', value: '<script>private</script>' }, output: { state: 'NOT_CAPTURED', value: null }, steps: [{ id: 'step', nodeId: '<img src=x>', status: 'FAILED', errorCode: 'EXECUTION_ERROR' }], stepsTruncated: true } };
const metric = { total: 1, statuses: { SUCCEEDED: 1 }, p95Millis: 1000, averageMillis: 1000, timeline: [], operations: [], partial: false };
const button = (element, text) => [...element.shadowRoot.querySelectorAll('button')].find(b => b.textContent === text);
const flush = () => new Promise(resolve => setImmediate(resolve));
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
function fixture() {
  const calls = []; const client = {
    runs: async (...args) => { calls.push(['runs', ...args]); return { items: [{ ...run, observation: null }], nextCursor: 'opaque-cursor' }; },
    run: async id => { calls.push(['run', id]); return structuredClone(run); },
    metrics: async (...args) => { calls.push(['metrics', ...args]); return metric; }
  };
  const element = document.createElement('gear4j-runs'); document.body.replaceChildren(element);
  return { element, calls, client };
}
test('client filters reads and sends SSE credentials only as headers, parsing chunked snapshots', async () => {
  const calls = []; const events = []; let cancelled = false;
  const encoded = new TextEncoder().encode('retry: 2000\n\nevent: snapshot\ndata: {"id":"observed"}\n\n: heartbeat\n\n');
  let index = 0;
  const client = new ManagementClient({ baseUrl: 'https://studio.example', tokenProvider: async () => 'synthetic-token', fetchImpl: async (url, init) => {
    calls.push([url, init]);
    if (!url.endsWith('/events')) return { ok: true, json: async () => ({}) };
    return { ok: true, headers: new Headers({ 'content-type': 'text/event-stream' }), body: { getReader: () => ({ read: async () => index < encoded.length ? { value: encoded.slice(index, index += 3), done: false } : { done: true }, cancel: async () => { cancelled = true; }, releaseLock() {} }) } };
  } });
  const filter = { from: '2026-10-04T10:00:00Z', until: '2026-10-04T11:00:00Z', mode: 'RUN', status: '', minimumDurationMillis: 0 };
  await client.runs('scope', filter, 'opaque/+'); await client.metrics('scope', filter); await client.run('observed'); await client.watchRun('observed', value => events.push(value));
  assert.deepEqual(events, [{ id: 'observed' }]); assert.equal(cancelled, true);
  assert.equal(calls.length, 4); assert.ok(calls.every(([url, init]) => init.method === 'GET' && !url.includes('synthetic-token')));
  const query = new URL(calls[0][0]).searchParams; assert.equal(query.get('after'), 'opaque/+'); assert.equal(query.get('minimumDurationMillis'), '0'); assert.equal(query.has('status'), false);
  assert.equal(calls[3][1].headers.Authorization, 'Bearer synthetic-token'); assert.equal(calls[3][1].cache, 'no-store');
});
test('component escapes observations, keeps stable filters across pages, and removes data on revoked access', async () => {
  const f = fixture(); const e = f.element;
  await e.configure({ client: f.client, scopeId: 'scope', canReadObservability: true });
  const filter = structuredClone(f.calls[0][2]);
  button(e, 'Runs suivants').click(); await flush();
  assert.deepEqual(f.calls.filter(c => c[0] === 'runs').at(-1), ['runs', 'scope', filter, 'opaque-cursor']);
  await e.select('observation');
  assert.match(e.shadowRoot.textContent, /REDACTED/); assert.match(e.shadowRoot.textContent, /NOT_CAPTURED/); assert.match(e.shadowRoot.textContent, /limite de capture/);
  assert.equal(e.shadowRoot.querySelector('script, img'), null);
  f.client.runs = async () => { throw { status: 403 }; }; await e.reload();
  assert.equal(e.selected, null); assert.deepEqual(e.items, []); assert.equal(e.metrics, null);
  assert.doesNotMatch(e.shadowRoot.textContent, /private|engine-id|hash/);
  assert.match(e.shadowRoot.textContent, /données affichées ont été retirées/); e.remove();
});
test('READ_RUNS alone never requests metrics and renders metadata-only results', async () => {
  const f = fixture(); f.client.run = async () => ({ ...run, observation: null });
  await f.element.configure({ client: f.client, scopeId: 'scope' }); await f.element.select('observation');
  assert.equal(f.calls.some(c => c[0] === 'metrics'), false);
  assert.match(f.element.shadowRoot.textContent, /READ_OBSERVABILITY absent/);
  assert.doesNotMatch(f.element.shadowRoot.textContent, /private|engine-id/); f.element.remove();
});
test('late responses cannot restore an earlier scope or filter', async () => {
  const f = fixture(); const slow = deferred(); f.client.runs = async scope => scope === 'old' ? slow.promise : { items: [], nextCursor: null };
  const pending = f.element.configure({ client: f.client, scopeId: 'old' });
  await f.element.configure({ client: f.client, scopeId: 'current' });
  slow.resolve({ items: [run], nextCursor: null }); await pending;
  assert.deepEqual(f.element.items, []);
  const oldFilter = deferred(); let calls = 0;
  f.client.runs = async () => ++calls === 1 ? oldFilter.promise : { items: [], nextCursor: null };
  const reload = f.element.reload();
  f.element.shadowRoot.querySelector('[name=status]').value = 'FAILED';
  f.element.shadowRoot.querySelector('form').dispatchEvent(new dom.window.Event('submit', { cancelable: true }));
  await flush(); oldFilter.resolve({ items: [run], nextCursor: null }); await reload;
  assert.equal(calls, 2); assert.deepEqual(f.element.items, []); assert.equal(f.element.filter.status, 'FAILED'); f.element.remove();
});
test('stream closure reloads the durable snapshot; unavailable streams use polling without retrying execution', async () => {
  const f = fixture(); const e = f.element; let reads = 0; let streams = 0;
  f.client.run = async () => { reads++; return { ...run, status: 'RUNNING' }; };
  f.client.watchRun = async () => { streams++; throw { status: 503 }; };
  await e.configure({ client: f.client, scopeId: 'scope' }); e.auto = true;
  await e.select('observation'); await flush();
  assert.equal(streams, 1); assert.equal(reads, 2); assert.equal(e.streamUnavailable, true);
  await e.tick(); assert.equal(streams, 1); assert.equal(reads, 3);
  e.stop(); e.streamUnavailable = false;
  f.client.watchRun = async (id, accept) => { streams++; accept({ ...run, status: 'SUCCEEDED' }); };
  f.client.run = async () => { reads++; return run; };
  e.selected = { ...run, status: 'RUNNING' }; await e.watch();
  assert.equal(e.selected.status, 'SUCCEEDED'); assert.equal(reads, 4); e.remove();
});
test('disconnection aborts SSE and permission revocation clears the current detail', async () => {
  const f = fixture(); const e = f.element; let signal; const end = deferred();
  f.client.watchRun = async (id, accept, options) => { signal = options.signal; await end.promise; accept(run); };
  await e.configure({ client: f.client, scopeId: 'scope' }); e.auto = true; e.selected = { ...run, status: 'RUNNING' };
  const pending = e.watch(); e.remove(); assert.equal(signal.aborted, true); end.resolve(); await pending;
  assert.equal(e.selected.status, 'RUNNING');
  const next = fixture(); await next.element.configure({ client: next.client, scopeId: 'scope' });
  next.element.auto = true; next.element.selected = { ...run, status: 'RUNNING' };
  next.client.watchRun = async () => { throw { status: 403 }; };
  await next.element.watch(); assert.equal(next.element.selected, null); assert.equal(next.element.auto, false); next.element.remove();
});
