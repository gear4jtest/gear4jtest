const css = `:host{display:block;margin:20px 0;color:#203f3b;font:14px/1.5 system-ui}*{box-sizing:border-box}section{padding:18px;border:1px solid #d9e3df;border-radius:10px;background:white;display:grid;gap:12px}h3,p{margin:0}.bar{display:flex;gap:8px;flex-wrap:wrap}label{display:grid;gap:4px;font-size:12px}input,select,textarea,button{font:inherit;padding:7px;border:1px solid #b8ccc4;border-radius:5px;background:white;color:inherit}textarea{min-height:70px;width:100%}button{cursor:pointer}button:disabled{opacity:.5;cursor:default}.form{display:grid;gap:10px;max-width:850px}code,pre{overflow-wrap:anywhere;white-space:pre-wrap;font-size:12px}.error{color:#a63728}small{color:#667c77}details{border-top:1px solid #d9e3df;padding-top:10px}summary{cursor:pointer}button:focus-visible{outline:3px solid #006b60}`;
const el = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
const button = (text, action, disabled = false) => { const node = el('button', text); node.type = 'button'; node.disabled = disabled; node.addEventListener('click', action); return node; };
const empty = () => ({ name: '', kind: 'SYNTHETIC_TEXT', value: '', fixtureVersion: '', outcome: '', compareOutput: false, output: '' });
/** Public component. Targets come from the host; all permissions/admission are rechecked by management. */
export class Gear4jScenarios extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this.items = []; this.form = empty(); this.message = ''; this.dirty = false; }
  async configure({ client, scopes, getTargets = () => [] }) {
    this.client = client; this.scopes = scopes.filter(s => s.permissions.includes('READ_SCENARIOS')); this.getTargets = getTargets;
    this.form = empty(); this.message = ''; this.history = null; this.items = []; this.targetKey = null;
    this.scope = this.scopes[0]; this.selected = null; this.revision = null; this.dirty = false; this.pending = null; this.result = null;
    if (this.scope) await this.reload(); else this.render();
  }
  get hasUnsavedChanges() { return this.dirty; }
  async perform(work) {
    if (this.busy) return; this.busy = true; this.error = false; this.message = 'Opération en cours…'; this.render();
    try { await work(); } catch (error) {
      this.error = true; this.message = ({ CONFLICT: 'Conflit : rechargez le scénario avant de sauvegarder. Votre saisie est conservée.', SCENARIO_DATA_NOT_ADMITTED: 'Fixture ou résultat attendu refusé par la politique de données de l’application.', SCENARIO_TARGET_MISMATCH: 'Un scénario de draft reste limité à ce draft ; choisissez une cible du même périmètre.', FORBIDDEN: 'Action non autorisée.', NOT_FOUND: 'Scénario introuvable ou inaccessible.' })[error.code] || 'Résultat non confirmé. Relisez l’état avant toute nouvelle action.';
      if ([401,403,404].includes(error.status)) { this.selected = null; this.revision = null; this.items = []; this.form = empty(); this.dirty = false; this.result = null; }
    } finally { this.busy = false; this.render(); }
  }
  async reload(after = null) { const page = await this.client.scenarios(this.scope.id, after); this.items = page.items; this.next = page.nextCursor; this.after = after; this.render(); }
  guard() { if (!this.dirty) return true; this.message = 'Enregistrez la saisie ou abandonnez les modifications locales avant de changer de scénario.'; this.render(); return false; }
  adopt(state, revision = state.revision) {
    this.selected = state; this.revision = revision; const data = revision.data;
    this.form = { name: state.scenario.name, kind: data.input.kind, value: data.input.value, fixtureVersion: data.input.fixtureVersion || '', outcome: data.expected?.outcome || '', compareOutput: data.expected?.output != null, output: data.expected?.output ?? '' };
    this.dirty = false; this.pending = null; this.result = null; this.history = null;
  }
  async choose(id) { if (!this.guard()) return; this.adopt(await this.client.scenario(id)); }
  data() { return { input: { kind: this.form.kind, value: this.form.value, fixtureVersion: this.form.kind === 'FIXTURE_REFERENCE' ? this.form.fixtureVersion : null }, expected: this.form.outcome ? { outcome: this.form.outcome, output: this.form.compareOutput ? this.form.output : null } : null }; }
  async save() {
    const state = this.selected ? await this.client.saveScenario(this.selected.scenario.id, this.selected.scenario.version, this.form.name, this.data()) : await this.client.createScenario(this.scope.id, this.form.name, this.data());
    this.adopt(state); await this.reload(); this.message = `Scénario enregistré · révision ${state.revision.number}.`;
    this.dispatchEvent(new CustomEvent('studio:scenario', { detail: { id: state.scenario.id, revision: state.revision.number }, bubbles: true, composed: true }));
  }
  async test() {
    const targets = this.getTargets(); const target = targets.find(t => t.key === this.targetKey);
    if (this.dirty || !this.revision || !target || target.disabled) { this.message = 'Enregistrez le scénario et choisissez une cible structurée enregistrée.'; return; }
    this.pending = { requestId: crypto.randomUUID(), revision: this.revision.number, target: structuredClone(target.target) };
    this.result = null;
    this.result = await this.client.testScenario(this.selected.scenario.id, this.pending);
    this.message = 'Test terminé ou admis. Le résultat du scénario est distinct du résultat Gear4J.';
    this.dispatchEvent(new CustomEvent('studio:run', { detail: { requestId: this.pending.requestId, state: this.result.state }, bubbles: true, composed: true }));
  }
  field(form, label, key, tag = 'input', options = null) {
    const wrapper = el('label', label); const input = el(tag); input.name = key;
    if (options) for (const [value, text] of options) { const o = el('option', text); o.value = value; input.append(o); }
    if (typeof this.form[key] === 'boolean') { input.type = 'checkbox'; input.checked = this.form[key]; } else { input.value = this.form[key]; input.maxLength = key === 'name' ? 160 : 8192; }
    input.disabled = this.busy || this.selected && !this.selected.actions.includes('EDIT') || !this.scope.permissions.includes('MANAGE_SCENARIOS');
    input.addEventListener(tag === 'select' || input.type === 'checkbox' ? 'change' : 'input', () => { this.form[key] = input.type === 'checkbox' ? input.checked : input.value; this.dirty = true; if (options || input.type === 'checkbox') this.render(); });
    wrapper.append(input); form.append(wrapper); return input;
  }
  render() {
    const root = this.shadowRoot; root.replaceChildren(el('style', css)); const box = el('section'); box.append(el('h3', 'Scénarios réutilisables'));
    const message = el('p', this.message); message.setAttribute('role', 'status'); if (this.error) message.className = 'error'; box.append(message);
    if (!this.scope) { box.append(el('p', 'Aucun périmètre de scénario accessible.')); root.append(box); return; }
    const scopeLabel = el('label', 'Visibilité du scénario'); const scope = el('select'); for (const s of this.scopes) { const o = el('option', s.label); o.value = s.id; scope.append(o); } scope.value = this.scope.id; scope.disabled = this.busy;
    scope.addEventListener('change', () => this.perform(async () => { if (!this.guard()) return; this.scope = this.scopes.find(s => s.id === scope.value); this.selected = null; this.revision = null; this.form = empty(); this.result = null; this.pending = null; await this.reload(); })); scopeLabel.append(scope); box.append(scopeLabel);
    box.append(el('small', 'Les fixtures héritent des droits de ce périmètre. Le nom et les valeurs enregistrées doivent être exempts de secrets. Une référence de fixture est distincte d’une référence de configuration secrète.'));
    const list = el('div'); list.className = 'bar'; for (const state of this.items) list.append(button(`${state.scenario.name} · r${state.scenario.head}${state.scenario.archived ? ' · archivé' : ''}`, () => this.perform(() => this.choose(state.scenario.id)), this.busy)); box.append(list);
    const paging = el('div'); paging.className = 'bar'; paging.append(button('Actualiser la liste', () => this.perform(() => this.reload()), this.busy), button('Scénarios suivants', () => this.perform(() => this.reload(this.next)), this.busy || !this.next));
    if (this.scope.permissions.includes('MANAGE_SCENARIOS')) paging.append(button('Nouveau scénario', () => { if (!this.guard()) return; this.selected = null; this.revision = null; this.form = empty(); this.pending = null; this.result = null; this.render(); }, this.busy)); box.append(paging);
    if (this.selected) box.append(el('code', `${this.selected.scenario.id} · révision sélectionnée ${this.revision.number} · tête ${this.selected.scenario.head}`));
    const form = el('div'); form.className = 'form'; this.field(form, 'Nom du scénario', 'name'); this.field(form, 'Source des données', 'kind', 'select', [['SYNTHETIC_TEXT','Texte synthétique admis'],['FIXTURE_REFERENCE','Fixture autorisée et versionnée']]);
    this.field(form, this.form.kind === 'FIXTURE_REFERENCE' ? 'Référence de fixture' : 'Entrée synthétique exacte', 'value', 'textarea'); if (this.form.kind === 'FIXTURE_REFERENCE') this.field(form, 'Version immuable de la fixture', 'fixtureVersion');
    this.field(form, 'Résultat Gear4J attendu', 'outcome', 'select', [['','Aucune assertion'], ...['SUCCEEDED','FAILED','CANCELLED','STOPPED','SKIPPED'].map(s=>[s,s])]);
    if (this.form.outcome) { this.field(form, 'Comparer la sortie String exacte (chaîne vide comprise)', 'compareOutput'); if (this.form.compareOutput) this.field(form, 'Sortie synthétique attendue', 'output', 'textarea'); }
    const editable = this.scope.permissions.includes('MANAGE_SCENARIOS') && (!this.selected || this.selected.actions.includes('EDIT'));
    if (editable) form.append(button('Enregistrer le scénario', () => this.perform(() => this.save()), this.busy));
    form.append(button('Abandonner les modifications locales', () => { if (this.selected) this.adopt(this.selected); else { this.form = empty(); this.dirty = false; } this.message = ''; this.render(); }, this.busy)); box.append(form);
    if (this.selected) {
      const controls = el('div'); controls.className = 'bar'; controls.append(button('Révisions du scénario', () => this.perform(async () => { if (!this.guard()) return; this.history = await this.client.scenarioRevisions(this.selected.scenario.id); }), this.busy));
      if (this.selected.actions.includes('ARCHIVE')) { const details = el('details'); details.append(el('summary','Archiver le scénario'), el('p','Les révisions et les résultats restent consultables. Aucun nouveau test ne sera admis.')); details.append(button('Confirmer l’archivage', () => this.perform(async () => { if (!this.guard()) return; this.adopt(await this.client.archiveScenario(this.selected.scenario.id, this.selected.scenario.version)); await this.reload(); }), this.busy)); controls.append(details); } box.append(controls);
      if (this.history) { for (const r of this.history.items) box.append(button(`Reprendre la révision ${r.number} · ${r.author}`, () => { if (!this.guard()) return; this.adopt(this.selected, r); this.render(); }, this.busy)); if (this.history.nextCursor) box.append(button('Révisions suivantes', () => this.perform(async () => { this.history = await this.client.scenarioRevisions(this.selected.scenario.id, this.history.nextCursor); }), this.busy)); }
      if (this.selected.actions.includes('SELECT_TARGET')) {
        const targets = this.getTargets(); if (!targets.some(t => t.key === this.targetKey)) this.targetKey = targets[0]?.key;
        const label = el('label', 'Cible exacte du test contrôlé'); const target = el('select'); target.name = 'target'; for (const t of targets) { const o = el('option', t.label); o.value = t.key; o.disabled = !!t.disabled; target.append(o); } target.value = this.targetKey || ''; target.disabled = this.busy; target.addEventListener('change', () => { this.targetKey = target.value; this.render(); }); label.append(target); box.append(label);
        box.append(button('Actualiser les cibles', () => this.render(), this.busy), button('Tester le scénario', () => this.perform(() => this.test()), this.busy || !targets.length));
      }
    }
    if (this.pending) { box.append(el('code', `Requête ${this.pending.requestId} · scénario r${this.pending.revision} · cible ${JSON.stringify(this.pending.target)}`)); box.append(button('Relire le reçu du test', () => this.perform(async () => { this.result = await this.client.testRequest(this.pending.requestId); this.message = 'Reçu relu, sans nouvelle exécution.'; }), this.busy)); }
    if (this.result) box.append(el('pre', JSON.stringify(this.result, null, 2)));
    root.append(box);
  }
}
if (!customElements.get('gear4j-scenarios')) customElements.define('gear4j-scenarios', Gear4jScenarios);
