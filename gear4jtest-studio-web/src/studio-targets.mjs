import './studio-workspace.mjs';
import { ManagementClient } from './management-client.mjs';
import { StudioError } from './studio-client.mjs';

/** Public federation client. Target ids come from the server inventory; never accept endpoint URLs. */
export class FederationClient {
  constructor({ baseUrl = '', tokenProvider = () => '', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, ''); this.tokenProvider = tokenProvider; this.fetch = fetchImpl;
  }
  async targets() {
    const token = await this.tokenProvider();
    const response = await this.fetch(`${this.baseUrl}/studio/api/v1/targets`, { cache: 'no-store', credentials: 'omit', headers: { Authorization: `Bearer ${token}` } });
    const data = await response.json();
    if (!response.ok) throw new StudioError(data.code || 'REQUEST_FAILED', response.status);
    return data;
  }
  management(targetId, runtimeContextId, onUnavailable = () => {}) {
    if (!/^[a-z][a-z0-9-]{0,63}$/.test(targetId)) throw new TypeError('Invalid target id');
    if (typeof runtimeContextId !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(runtimeContextId)) throw new TypeError('Invalid runtime context');
    const client = new ManagementClient({ baseUrl: `${this.baseUrl}/targets/${targetId}`, tokenProvider: this.tokenProvider,
      fetchImpl: (url, options) => this.fetch(url, { ...options, headers: { ...options.headers, 'X-Gear4J-Expected-Context': runtimeContextId } }) });
    const request = client.request.bind(client);
    client.request = async (...args) => {
      try { return await request(...args); }
      catch (error) {
        if (['UNAUTHENTICATED', 'TARGET_UNAVAILABLE', 'REMOTE_ACCESS_DENIED', 'REMOTE_CONTEXT_MISMATCH', 'REMOTE_IDENTITY_MISMATCH', 'REMOTE_PROTOCOL_MISMATCH'].includes(error.code)) onUnavailable(error);
        throw error;
      }
    };
    return client;
  }
}

const el = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; return node; };
const button = (text, action, disabled = false) => { const node = el('button', text); node.type = 'button'; node.disabled = disabled; node.addEventListener('click', action); return node; };
const errors = {
  REMOTE_CONTEXT_MISMATCH: 'La cible répond avec un autre contexte runtime. Connexion bloquée.',
  REMOTE_IDENTITY_MISMATCH: 'La cible reconnaît un autre utilisateur. Connexion bloquée.',
  REMOTE_PROTOCOL_MISMATCH: 'Le contrat de management de la cible doit être mis à jour.',
  REMOTE_ACCESS_DENIED: 'Accès refusé par la cible.',
  UNAUTHENTICATED: 'Votre connexion a expiré.',
  TARGET_UNAVAILABLE: 'Cette cible n’est plus accessible.',
  REMOTE_UNAVAILABLE: 'La cible est indisponible. Vous pouvez réessayer sa connexion.',
  REMOTE_AUTH_UNAVAILABLE: 'Les credentials de cette cible sont indisponibles.'
};

/** Target selector consuming the public workspace. Each target gets a separate client and element. */
export class Gear4jTargets extends HTMLElement {
  constructor() {
    super(); this.attachShadow({ mode: 'open' }); this.epoch = 0;
    const style = el('style'); style.textContent = ':host{display:block;font:14px/1.5 system-ui;color:#203f3b}.controls{display:grid;gap:10px;padding:16px;border:1px solid #c6d8ce;border-radius:8px;background:#fff}select,button{font:inherit;padding:8px;border:1px solid #b8ccc4;border-radius:5px}label{display:grid;gap:5px}button{cursor:pointer;background:#eef6f2;color:#205044}p{margin:0}.actions{display:flex;gap:8px;flex-wrap:wrap}';
    this.controls = el('section'); this.controls.className = 'controls'; this.content = el('div');
    this.shadowRoot.append(style, this.controls, this.content);
  }
  async configure({ client }) {
    const epoch = ++this.epoch; this.client = client; this.clear(); this.targets = []; this.identity = ''; this.loading = true; this.message = 'Connexion…'; this.render();
    try {
      const inventory = await client.targets(); if (epoch !== this.epoch) return;
      this.targets = inventory.targets; this.identity = inventory.identity; this.message = '';
    } finally { if (epoch === this.epoch) { this.loading = false; this.render(); } }
  }
  clear() { this.content.replaceChildren(); this.content.inert = false; this.workspace = null; this.selected = null; this.pending = null; this.loading = false; }
  disconnectedCallback() { ++this.epoch; this.clear(); this.client = null; }
  disconnect(discard = false) {
    if (this.workspace?.hasPendingActions) { this.message = 'Attendez la fin de l’action en cours.'; this.render(); return; }
    if (this.hasUnsavedChanges && !discard) { this.pending = '__disconnect'; this.message = 'Confirmez l’abandon des modifications avant de vous déconnecter.'; this.render(); return; }
    ++this.epoch; this.clear(); this.client = null; this.targets = []; this.identity = ''; this.message = 'Déconnecté.'; this.render();
    this.dispatchEvent(new CustomEvent('studio:disconnect', { bubbles: true, composed: true }));
  }
  get hasUnsavedChanges() { return !!this.workspace?.hasUnsavedChanges; }
  async refresh() {
    const epoch = this.epoch;
    try {
      const inventory = await this.client.targets(); if (epoch !== this.epoch) return; this.targets = inventory.targets;
      if (this.identity !== inventory.identity || this.selected && !this.targets.some(t => t.id === this.selected.id && t.runtimeContextId === this.selected.runtimeContextId)) { ++this.epoch; this.clear(); this.message = 'L’identité ou la cible a changé. Sélectionnez à nouveau une cible.'; }
      this.identity = inventory.identity;
    } catch (error) { if (epoch !== this.epoch) return; ++this.epoch; this.clear(); this.message = errors[error.code] || 'Inventaire indisponible.'; }
    this.render();
  }
  async selectTarget(id, discard = false) {
    const target = this.targets.find(t => t.id === id); if (!target || id === this.selected?.id) return;
    if (this.workspace?.hasPendingActions) { this.message = 'Attendez la fin de l’action en cours avant de changer de cible.'; this.render(); return; }
    if (this.hasUnsavedChanges && !discard) { this.pending = id; this.message = 'Des modifications locales ne sont pas enregistrées.'; this.render(); return; }
    const epoch = ++this.epoch; this.loading = true; this.content.inert = true; this.pending = null; this.message = `Connexion à ${target.name}…`; this.render();
    const candidate = document.createElement('gear4j-workspace');
    try {
      const client = this.client.management(id, target.runtimeContextId, error => {
        if (this.workspace === candidate) { ++this.epoch; this.clear(); this.message = errors[error.code] || 'Accès interrompu.'; this.render(); }
      });
      const context = await candidate.configure({ client });
      if (epoch !== this.epoch) return;
      if (context.context.id !== target.runtimeContextId) throw new StudioError('REMOTE_CONTEXT_MISMATCH', 409);
      this.workspace = candidate; this.selected = target; this.content.replaceChildren(candidate);
      this.message = `${target.applicationName} · ${target.name} · ${context.context.id}`;
      this.dispatchEvent(new CustomEvent('studio:target', { detail: { targetId: target.id, runtimeContextId: target.runtimeContextId }, bubbles: true, composed: true }));
    } catch (error) { if (epoch === this.epoch) this.message = errors[error.code] || 'Connexion à la cible impossible.'; }
    finally { if (epoch === this.epoch) { this.loading = false; this.content.inert = false; this.render(); } }
  }
  render() {
    this.controls.replaceChildren(); this.controls.append(el('strong', `Applications accessibles · ${this.identity || ''}`));
    const label = el('label', 'Application / cible'); const select = el('select'); const empty = el('option', 'Choisir une cible'); empty.value = ''; select.append(empty);
    const groups = new Map();
    for (const target of this.targets || []) {
      if (!groups.has(target.applicationId)) { const group = el('optgroup'); group.label = target.applicationName; groups.set(target.applicationId, group); select.append(group); }
      const option = el('option', target.name); option.value = target.id; groups.get(target.applicationId).append(option);
    }
    select.value = this.selected?.id || ''; select.disabled = !!this.loading;
    select.addEventListener('change', () => this.selectTarget(select.value)); label.append(select); this.controls.append(label);
    const actions = el('div'); actions.className = 'actions'; actions.append(button('Actualiser les cibles', () => this.refresh(), this.loading || !this.client));
    if (this.client) actions.append(button('Se déconnecter', () => this.disconnect(), this.loading));
    if (this.pending) actions.append(button('Abandonner les modifications et changer', () => this.pending === '__disconnect' ? this.disconnect(true) : this.selectTarget(this.pending, true)), button('Rester ici', () => { this.pending = null; this.message = ''; this.render(); }));
    this.controls.append(actions); const status = el('p', this.message); status.setAttribute('role', 'status'); this.controls.append(status);
    if (!this.targets?.length) this.controls.append(el('p', 'Aucune cible autorisée.'));
  }
}
if (!customElements.get('gear4j-targets')) customElements.define('gear4j-targets', Gear4jTargets);
