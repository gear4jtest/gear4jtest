/** Public transport client. The host owns authentication; no token persistence and no automatic POST retry. */
export class StudioError extends Error {
  constructor(code, status) { super(code); this.code = code; this.status = status; }
}
export class StudioTransport {
  constructor({ baseUrl = '', tokenProvider = () => '', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, ''); this.tokenProvider = tokenProvider; this.fetch = fetchImpl;
  }
  async request(path, method = 'GET', body) {
    const token = await this.tokenProvider();
    const response = await this.fetch(`${this.baseUrl}/api/v1${path}`, {
      method, cache: 'no-store', credentials: 'omit',
      headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    const value = await response.json();
    if (!response.ok) throw new StudioError(value.code || 'REQUEST_FAILED', response.status);
    return value;
  }
  test(id, { requestId, revision, input }) {
    if (!requestId) throw new TypeError('requestId required');
    return this.request(`/drafts/${encodeURIComponent(id)}/tests`, 'POST', { requestId, revision, input });
  }
  testRequest(id) { return this.request(`/tests/${encodeURIComponent(id)}`); }
}
export function createOperation(descriptor, id = `step_${crypto.randomUUID().replaceAll('-', '').slice(0, 12)}`) {
  return { kind: 'operation', id, operationId: descriptor.id, parameters: Object.fromEntries(
    Object.entries(descriptor.parameters).map(([name, p]) => [name, { kind: p.kind, value: p.kind === 'RESOURCE_REFERENCE' ? p.allowedReferences[0] || '' : '' }])) };
}
export function moveNode(definition, index, delta) {
  const copy = structuredClone(definition); const target = index + delta;
  if (index >= 0 && index < copy.nodes.length && target >= 0 && target < copy.nodes.length) {
    [copy.nodes[index], copy.nodes[target]] = [copy.nodes[target], copy.nodes[index]];
  }
  return copy;
}
