import { StudioTransport } from './studio-client.mjs';
/** Public management client. Identities/groups are never accepted as request options. */
export class ManagementClient extends StudioTransport {
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
