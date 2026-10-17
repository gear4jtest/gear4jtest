import { StudioTransport, StudioError } from './studio-client.mjs';
/** Public management client. Identities/groups are never accepted as request options. */
export class ManagementClient extends StudioTransport {
  runs(scope, filter, after, limit = 25) {
    const query = new URLSearchParams(Object.entries(filter).filter(([, v]) => v !== '' && v != null));
    query.set('limit', String(limit)); if (after) query.set('after', after);
    return this.request(`/resources/${encodeURIComponent(scope)}/runs?${query}`);
  }
  metrics(scope, filter) {
    const query = new URLSearchParams(Object.entries(filter).filter(([, v]) => v !== '' && v != null));
    return this.request(`/resources/${encodeURIComponent(scope)}/metrics?${query}`);
  }
  run(id) { return this.request(`/runs/${encodeURIComponent(id)}`); }
  /** One best-effort SSE connection. Caller reloads a snapshot on reconnect or uses polling. */
  async watchRun(id, onSnapshot, { signal } = {}) {
    const token = await this.tokenProvider();
    const response = await this.fetch(`${this.baseUrl}/api/v1/runs/${encodeURIComponent(id)}/events`, {
      method: 'GET', cache: 'no-store', credentials: 'omit', signal,
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }
    });
    if (!response.ok) throw new StudioError('STREAM_UNAVAILABLE', response.status);
    if (!response.headers.get('content-type')?.startsWith('text/event-stream') || !response.body?.getReader)
      throw new StudioError('STREAM_UNAVAILABLE', 503);
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
    try {
      while (!signal?.aborted) {
        const { value, done } = await reader.read(); if (done) break;
        buffer += decoder.decode(value, { stream: true });
        if (buffer.length > 524288) throw new StudioError('STREAM_INVALID', 503);
        let end;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2);
          if (frame.startsWith('event: unavailable')) throw new StudioError('STREAM_UNAVAILABLE', 403);
          if (!frame.startsWith('event: snapshot\n')) continue;
          const payload = frame.split('\n').filter(line => line.startsWith('data: ')).map(line => line.slice(6)).join('\n');
          onSnapshot(JSON.parse(payload));
        }
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
  }
  versions(chainId, after) { return this.request(`/chains/${encodeURIComponent(chainId)}/versions?limit=25${after ? `&after=${encodeURIComponent(after)}` : ''}`); }
  version(id) { return this.request(`/versions/${encodeURIComponent(id)}`); }
  createVersion(id, revision, label) { return this.request(`/drafts/${encodeURIComponent(id)}/versions`, 'POST', { revision, label }); }
  publishTest(id) { return this.request(`/versions/${encodeURIComponent(id)}/publish`, 'POST', {}); }
  promoteRun(id) { return this.request(`/versions/${encodeURIComponent(id)}/promote`, 'POST', {}); }
  refreshVersion(id) { return this.request(`/versions/${encodeURIComponent(id)}/refresh`, 'POST', {}); }
  compareVersions(id, otherVersionId) { return this.request(`/versions/${encodeURIComponent(id)}/compare`, 'POST', { otherVersionId }); }
  testVersion(id, request) { return this.request(`/versions/${encodeURIComponent(id)}/tests`, 'POST', request); }
  context() { return this.request('/context'); }
  catalog() { return this.request('/catalog'); }
  draft(id) { return this.request(`/drafts/${encodeURIComponent(id)}`); }
  list(kind, { parent, after, limit = 25 } = {}) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (parent) query.set('parent', parent); if (after) query.set('after', after);
    return this.request(`/${kind}?${query}`);
  }
  resource(id) { return this.request(`/resources/${encodeURIComponent(id)}`); }
  createDomain(key, name, description = '') { return this.request('/domains', 'POST', { key, name, description }); }
  createChain(domainId, key, name, description = '', runtimeAssemblyLineId = `chain_${crypto.randomUUID().replaceAll('-', '')}`) { return this.request('/chains', 'POST', { domainId, key, name, description, runtimeAssemblyLineId }); }
  createDraft(chainId, name, definition) { return this.request('/drafts', 'POST', { chainId, name, definition }); }
  save(id, expectedVersion, definition) { return this.request(`/drafts/${encodeURIComponent(id)}`, 'PUT', { expectedVersion, definition }); }
  saveSource(id, expectedVersion, source) { return this.request(`/drafts/${encodeURIComponent(id)}/source`, 'PUT', { expectedVersion, mediaType: 'application/xml', source }); }
  metadata(id, expectedVersion, name, description = '') { return this.request(`/resources/${encodeURIComponent(id)}`, 'PUT', { expectedVersion, name, description }); }
  access(id) { return this.request(`/resources/${encodeURIComponent(id)}/access`); }
  setAccess(id, expectedVersion, policy) { return this.request(`/resources/${encodeURIComponent(id)}/access`, 'PUT', { expectedVersion, policy }); }
  archive(id, expectedVersion) { return this.request(`/resources/${encodeURIComponent(id)}/archive`, 'POST', { expectedVersion }); }
  audit(id, after) { return this.request(`/resources/${encodeURIComponent(id)}/audit?limit=25${after ? `&after=${encodeURIComponent(after)}` : ''}`); }
  revisions(id, after) { return this.request(`/drafts/${encodeURIComponent(id)}/revisions?limit=25${after ? `&after=${encodeURIComponent(after)}` : ''}`); }
  fork(id, revision, name) { return this.request(`/drafts/${encodeURIComponent(id)}/fork`, 'POST', { revision, name }); }
  validate(definition, scope) { return this.request(`/resources/${encodeURIComponent(scope)}/validate`, 'POST', { definition }); }
  exportXml(definition, scope) { return this.request(`/resources/${encodeURIComponent(scope)}/export`, 'POST', { definition }); }
  previewXml(scope, source) { return this.request(`/resources/${encodeURIComponent(scope)}/xml-preview`, 'POST', { source }); }
  diff(id, revision, definition) { return this.request(`/drafts/${encodeURIComponent(id)}/diff`, 'POST', { revision, definition }); }
  compare(id, fromRevision, toRevision) { return this.request(`/drafts/${encodeURIComponent(id)}/compare`, 'POST', { fromRevision, toRevision }); }
  diagnose(definition, scope) { return this.request(`/resources/${encodeURIComponent(scope)}/diagnostics`, 'POST', { definition }); }
  importDraft(chainId, name, source) { return this.request('/import', 'POST', { chainId, name, source }); }
}
/** One adapter per editor: preserves the distinction between revision number and optimistic resource version. */
export class ScopedAuthoringClient {
  constructor(client, state, context, onState = () => {}) { this.client = client; this.state = state; this.info = context; this.onState = onState; }
  actions(state) {
    return state.permissions.filter(action => action !== 'TEST' && action !== 'VALIDATE' ||
      this.info.context.capabilities?.includes(action === 'TEST' ? 'CONTROLLED_TEST' : 'VALIDATE'));
  }
  flat(state) { return { ...state.revision, draftVersion: state.draft.version, actions: this.actions(state) }; }
  adopt(state) { this.state = state; this.onState(state); return this.flat(state); }
  async context() {
    let canImport = false;
    try { canImport = (await this.client.resource(this.state.draft.chainId)).permissions.includes('CREATE_DRAFT'); } catch { /* Parent may be private. */ }
    return { ...this.info, persistent: true, actions: [...this.actions(this.state), ...(canImport ? ['IMPORT'] : [])], sample: this.state.revision.definition };
  }
  catalog() { return this.client.catalog(); }
  async draft(id) { return this.adopt(await this.client.draft(id)); }
  async save(id, expectedVersion, definition) { return this.adopt(await this.client.save(id, expectedVersion, definition)); }
  async saveSource(id, expectedVersion, source) { return this.adopt(await this.client.saveSource(id, expectedVersion, source)); }
  async create(definition) { return this.adopt(await this.client.createDraft(this.state.draft.chainId, 'Nouveau draft', definition)); }
  validate(definition) { return this.client.validate(definition, this.state.draft.id); }
  diagnose(definition) { return this.client.diagnose(definition, this.state.draft.id); }
  previewXml(source) { return this.client.previewXml(this.state.draft.id, source); }
  diff(definition) { return this.client.diff(this.state.draft.id, this.state.revision.number, definition); }
  exportXml(definition) { return this.client.exportXml(definition, this.state.draft.id); }
  async importXml(source) { return this.adopt(await this.client.importDraft(this.state.draft.chainId, `${this.state.draft.name.slice(0,145)} (XML)`, source)); }
  test(id, request) { return this.client.test(id, request); }
  testRequest(id) { return this.client.testRequest(id); }
}
