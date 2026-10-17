import { comparisonPanel } from './studio-element.mjs';
const el = (tag, text) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; return node; };
const button = (text, action, disabled = false) => { const node = el('button', text); node.type = 'button'; node.disabled = disabled; node.addEventListener('click', action); return node; };
const field = (text, value = '') => { const label = el('label', text); const input = el('input'); input.value = value; label.append(input); return { label, input }; };
const css = `:host{display:block;font:14px/1.5 system-ui;color:#203f3b}*{box-sizing:border-box}section{border:1px solid #d9e3df;border-radius:10px;background:#fff;padding:16px;display:grid;gap:12px}h3,p{margin:0}.bar{display:flex;gap:8px;flex-wrap:wrap;align-items:center}button,input,select{font:inherit;padding:7px;border:1px solid #b8ccc4;border-radius:5px}button{cursor:pointer;background:#fff;color:#205044}button:disabled{opacity:.5;cursor:default}label{display:grid;gap:4px}small{color:#667c77}pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:350px;overflow:auto;font-size:12px}code{overflow-wrap:anywhere}table{width:100%;font-size:12px;border-collapse:collapse}td,th{text-align:left;vertical-align:top;padding:7px;border-bottom:1px solid #d9e3df;overflow-wrap:anywhere}.error{color:#a63728}`;
/** Public lifecycle component; host authentication and ACL decisions stay in the management API. */
export class Gear4jVersions extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this.message = ''; this.label = ''; this.input = ''; }
  async configure({ client, chainId, getDraft = () => null, canFreeze = false }) {
    this.client = client; this.chainId = chainId; this.getDraft = getDraft; this.canFreeze = canFreeze;
    this.selected = null; this.comparison = null; this.testResult = null; this.testRequest = null;
    await this.reload(); return this;
  }
  async reload(after = null) { this.page = await this.client.versions(this.chainId, after); this.cursor = after; this.render(); }
  async perform(work) {
    if (this.busy) return; this.busy = true; this.error = false; this.message = 'Opération en cours…'; this.render();
    try { await work(); } catch (e) {
      this.error = true; this.message = ({ SOURCE_REQUIRES_ANALYSIS: 'Analysez le XML puis enregistrez une révision structurée.', INVALID_DEFINITION: 'La validation refuse cette définition.', CONFLICT: 'Ce libellé existe déjà ou la ressource a changé. Rechargez l’historique.', FORBIDDEN: 'Action non autorisée.', NOT_FOUND: 'Version inaccessible.', INVALID_REQUEST: 'Vérifiez le libellé de version.', TEST_PUBLICATION_REQUIRED: 'Une publication TEST confirmée est nécessaire.' })[e.code] || 'Résultat non confirmé. Actualisez l’état avant toute nouvelle demande.';
    } finally { this.busy = false; this.render(); }
  }
  choose(state) { this.selected = state; this.comparison = null; this.testResult = null; this.testRequest = null; this.message = ''; this.render(); }
  async freeze() {
    const draft = this.getDraft();
    if (!draft || draft.dirty || draft.revision.kind === 'SOURCE') { this.message = 'Enregistrez une révision structurée avant de figer la version.'; return; }
    this.selected = await this.client.createVersion(draft.revision.draftId, draft.revision.number, this.label);
    this.testResult = null; this.testRequest = null; this.comparison = null; this.label = '';
    await this.reload(); this.message = 'Version figée. Son contenu est maintenant accessible aux lecteurs de la chaîne.';
  }
  async publish(mode) {
    const id = this.selected.version.id;
    this.selected = mode === 'TEST' ? await this.client.publishTest(id) : await this.client.promoteRun(id);
    await this.reload(this.cursor); this.message = 'Résultat relu dans Gear4J. Consultez l’état ci-dessous.';
  }
  render() {
    const root = this.shadowRoot; root.replaceChildren(el('style', css)); const box = el('section'); root.append(box);
    box.append(el('h3', 'Versions et publications')); const status = el('p', this.message); status.setAttribute('role', 'status'); if (this.error) status.className = 'error'; box.append(status);
    if (this.canFreeze && this.getDraft()) {
      const label = field('Libellé immuable (ex. 1.0.0)', this.label); label.input.maxLength = 64; label.input.addEventListener('input', () => { this.label = label.input.value; });
      box.append(label.label, el('small', 'Figer partage cette révision avec les lecteurs de la chaîne. Le contenu devra être valide ; la publication TEST reste une action distincte.'), button('Figer une version', () => this.perform(() => this.freeze()), this.busy || this.getDraft()?.revision.kind === 'SOURCE'));
    }
    const list = el('div'); list.className = 'bar';
    for (const state of this.page?.items || []) list.append(button(state.version.label, () => this.perform(async () => { this.choose(await this.client.version(state.version.id)); }), this.busy));
    box.append(list); const pages = el('div'); pages.className = 'bar'; pages.append(button('Début de l’historique', () => this.perform(() => this.reload()), this.busy || !this.cursor), button('Versions suivantes', () => this.perform(() => this.reload(this.page.nextCursor)), this.busy || !this.page?.nextCursor)); box.append(pages);
    if (!this.selected) { box.append(el('small', 'Sélectionnez une version pour consulter son artefact, la tester, la publier en TEST ou la promouvoir en RUN.')); return; }
    const { version, publications, actions } = this.selected;
    box.append(el('strong', `${version.runtimeAssemblyLineId} · ${version.label}`), el('small', `${version.author} · ${version.createdAt} · révision ${version.revision}`), el('code', `SHA-256 ${version.artifactHash}`));
    if (this.contextFingerprint && this.contextFingerprint !== version.catalogFingerprint) box.append(el('p', 'Le catalogue a changé depuis la création de cette version.'));
    const table = el('table'); const headings = el('tr'); for (const text of ['Mode', 'État', 'Demande', 'Publication observée']) headings.append(el('th', text)); table.append(headings);
    for (const mode of ['TEST', 'RUN']) { const p = publications.find(p => p.mode === mode); const row = el('tr'); for (const text of [mode, p ? `${p.state}${p.errorCode ? ` · ${p.errorCode}` : ''}` : 'Non demandée', p ? `${p.requestedBy} · ${p.requestedAt}` : '—', p?.publishedAt ? `${p.publishedBy} · ${p.publishedAt}` : '—']) row.append(el('td', text)); table.append(row); } box.append(table);
    const controls = el('div'); controls.className = 'bar';
    const blocked = mode => this.busy || ['REQUESTED', 'CONFIRMED'].includes(publications.find(p => p.mode === mode)?.state);
    controls.append(button('Actualiser les publications', () => this.perform(async () => { this.selected = await this.client.refreshVersion(version.id); await this.reload(this.cursor); this.message = 'État relu dans Gear4J.'; }), this.busy));
    if (actions.includes('PUBLISH')) controls.append(button('Publier en TEST', () => this.perform(() => this.publish('TEST')), blocked('TEST')));
    if (actions.includes('PROMOTE')) controls.append(button('Promouvoir en RUN', () => this.perform(() => this.publish('RUN')), blocked('RUN')));
    box.append(controls, el('small', 'REQUESTED ou UNKNOWN : relire l’état avant une reprise explicite. RUN confirme une publication Gear4J ; le choix de latest reste celui du runtime.'));
    const xml = el('details'); xml.append(el('summary', 'Artefact XML exact'), el('pre', version.source)); box.append(xml);
    const candidates = (this.page?.items || []).filter(v => v.version.id !== version.id);
    if (candidates.length) { const select = el('select'); select.setAttribute('aria-label', 'Version à comparer'); for (const candidate of candidates) { const option = el('option', candidate.version.label); option.value = candidate.version.id; select.append(option); } box.append(select, button('Comparer les versions', () => this.perform(async () => { this.comparison = await this.client.compareVersions(version.id, select.value); this.message = 'Comparaison des contenus figés.'; }), this.busy)); }
    if (this.comparison) box.append(comparisonPanel(this.comparison));
    if (actions.includes('TEST')) {
      const input = field('Entrée du test contrôlé (String)', this.input); input.input.maxLength = 8192; input.input.addEventListener('input', () => { this.input = input.input.value; });
      box.append(input.label, el('small', 'Le test exécute les opérations autorisées sur cet artefact exact. Il ne simule pas leurs effets et ne modifie pas TEST/RUN.'), button('Tester cette version', () => this.perform(async () => {
        this.testRequest = crypto.randomUUID(); this.testResult = null;
        this.testResult = await this.client.testVersion(version.id, { requestId: this.testRequest, input: this.input }); this.message = 'Résultat du test contrôlé.';
      }), this.busy));
      if (this.testRequest) box.append(button('Relire le résultat du test', () => this.perform(async () => { this.testResult = await this.client.testRequest(this.testRequest); this.message = 'Résultat relu.'; }), this.busy));
    }
    if (this.testResult) box.append(el('pre', JSON.stringify(this.testResult, null, 2)));
  }
}
if (!customElements.get('gear4j-versions')) customElements.define('gear4j-versions', Gear4jVersions);
