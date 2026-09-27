import { createOperation, moveNode } from './studio-client.mjs';
const styles = `
:host{display:block;color:#203f3b;font:14px/1.5 system-ui,sans-serif;--line:#d9e3df;--muted:#667c77;--brand:#006b60}
*{box-sizing:border-box}button,input,select,textarea{font:inherit}button{cursor:pointer;border:1px solid var(--line);background:#fff;color:#23453e;border-radius:7px;padding:8px 12px;font-weight:600}button:hover:enabled{background:#edf6f2;border-color:#8cae9e}button:disabled{opacity:.45;cursor:default}button.primary{background:var(--brand);color:white;border-color:var(--brand)}button.small{font-size:12px;padding:4px 8px}input,select,textarea{border:1px solid #b8ccc4;background:#fff;border-radius:6px;color:#203f3b;padding:8px;width:100%}textarea{resize:vertical}input:focus,select:focus,textarea:focus,button:focus-visible{outline:3px solid #b1dace;outline-offset:2px}label{display:grid;gap:5px;font-size:12px;color:var(--muted)}h2,h3,p{margin:0}h2{font-size:15px}h3{font-size:14px}small,.muted{color:var(--muted)}code,pre,.mono{font-family:ui-monospace,monospace;font-size:12px}code{overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.toolbar{display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:18px}.toolbar h2{font-size:21px}.actions{display:flex;gap:8px;flex-wrap:wrap}.status{min-height:25px;font-size:13px;margin-bottom:12px}.status.error{color:#a63728}.grid{display:grid;grid-template-columns:240px minmax(320px,1fr) 300px;gap:16px;align-items:start}.panel{background:#fff;border:1px solid var(--line);border-radius:12px;overflow:hidden}.heading{padding:17px 18px;border-bottom:1px solid var(--line);display:flex;gap:8px;justify-content:space-between;align-items:center}.pad{padding:18px;display:grid;gap:14px}.tag{font-size:10px;text-transform:uppercase;letter-spacing:.08em;background:#e5f3ec;color:#256547;border-radius:4px;padding:4px 7px}.catalog{padding:10px;display:grid;gap:4px}.catalog-card{padding:12px;border-radius:8px;background:#f7faf8;display:grid;gap:6px}.catalog-card .top{display:flex;justify-content:space-between;gap:6px}.contract{font:11px ui-monospace,monospace;color:#6d8078}.canvas{background:#f4f8f5;min-height:300px;padding:20px}.endpoint{text-align:center;text-transform:uppercase;letter-spacing:.09em;font-size:10px;color:#627b6b;padding:0 0 12px}.endpoint:last-child{padding:12px 0 0}.node{background:white;border:1px solid #cddbd3;border-left:3px solid #41977c;border-radius:8px;padding:12px;box-shadow:0 3px 9px #153d2810}.node-wrap+.node-wrap:before{content:'↓';display:block;text-align:center;color:#84a38e;height:26px;line-height:26px}.node-top{display:flex;align-items:start;gap:10px;justify-content:space-between}.node-top .title{display:grid;gap:3px}.node-top code{color:#7d9087;font-size:10px}.node .params{display:grid;gap:8px;margin-top:10px}.node .tools{display:flex;gap:3px}.node .tools button{padding:2px 7px;font-size:12px}.choice{border-left-color:#bd8a3c;background:#fffdf7}.branches{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:12px}.branches .node{padding:9px;box-shadow:none;border-left-width:1px}.branch-label{color:#967641;font-size:10px;text-transform:uppercase;margin-bottom:5px}.empty{text-align:center;padding:25px 8px;color:var(--muted)}.foot{padding:14px;display:flex;justify-content:center;gap:8px}.test-intro{background:#f5f8f5;padding:10px;border-radius:6px;font-size:12px}.run-status{font-weight:700;font-size:18px}.steps{list-style:none;margin:0;padding:0;display:grid;gap:7px}.steps li{display:flex;justify-content:space-between;gap:6px;border-bottom:1px solid #edf2ed;padding-bottom:5px;font-size:12px}.run-detail{display:grid;gap:9px;word-break:break-word}.capture{border:1px solid var(--line);border-radius:6px;padding:9px}.capture small{display:block;margin-bottom:4px}.lower{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:16px}.diagnostics{list-style:none;margin:0;padding:0;display:grid;gap:10px}.diagnostics li{border-left:3px solid #be643b;padding-left:10px}.diagnostics code{display:block;color:var(--muted);font-size:11px}.success{color:#28784f}details summary{cursor:pointer;font-size:12px;color:var(--muted)}details .pad{padding:12px 0 0}.xml{min-height:170px;font:12px/1.5 ui-monospace,monospace}.notice{font-size:11px;color:var(--muted);margin-top:16px}.busy{opacity:.65;pointer-events:none}@media(max-width:1100px){.grid{grid-template-columns:210px minmax(300px,1fr)}.test-panel{grid-column:1/-1}.test-panel .pad{grid-template-columns:1fr 1fr}.test-panel .run-detail{grid-column:1/-1}}@media(max-width:700px){.grid,.lower{grid-template-columns:1fr}.test-panel{grid-column:auto}.test-panel .pad{grid-template-columns:1fr}.branches{grid-template-columns:1fr}.toolbar{align-items:start}.catalog{max-height:290px;overflow:auto}}`;
function el(tag, text, className) { const n = document.createElement(tag); if (text !== undefined) n.textContent = text; if (className) n.className = className; return n; }
function button(text, action, disabled = false, className = '') { const n = el('button', text, className); n.type = 'button'; n.disabled = disabled; n.addEventListener('click', action); return n; }
function field(label, value, onInput, { multiline = false, disabled = false, className = '' } = {}) {
  const wrapper = el('label', label); const input = el(multiline ? 'textarea' : 'input'); input.value = value; input.disabled = disabled; input.className = className;
  input.addEventListener('input', () => onInput(input.value)); wrapper.append(input); return wrapper;
}
/** Public reusable component. configure({client}), studio:revision and studio:run are the integration surface. */
export class Gear4jStudio extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this.status = ''; this.xmlSource = ''; this.testInput = '  electric  '; }
  async configure({ client }) {
    const [context, catalog] = await Promise.all([client.context(), client.catalog()]);
    this.client = client; this.context = context; this.catalog = catalog; this.definition = structuredClone(context.sample);
    this.revision = null; this.dirty = true; this.report = null; this.submission = null; this.pendingTest = null; this.xmlSource = ''; this.status = 'Catalogue de l’application connecté.'; this.error = false; this.render(); return context;
  }
  allowed(action) { return this.context.actions.includes(action) && !this.busy; }
  touch() { this.dirty = true; this.report = null; this.xmlSource = ''; this.error = false; this.status = 'Modifications locales — enregistrez une révision avant de tester.'; this.updateToolbar();
    this.shadowRoot.querySelector('[data-validation]')?.replaceWith(this.validationPanel());
    const xml = this.shadowRoot.querySelector('textarea.xml'); if (xml) xml.value = '';
    const stale = this.shadowRoot.querySelector('[data-run-stale]'); if (stale) stale.hidden = false;
  }
  updateToolbar() {
    const status = this.shadowRoot.querySelector('.status'); if (status) status.textContent = this.status;
    const test = this.shadowRoot.querySelector('[data-action="test"]'); if (test) test.disabled = !this.allowed('TEST') || this.dirty || !this.revision;
    const badge = this.shadowRoot.querySelector('[data-revision]'); if (badge) badge.textContent = this.revision ? `Révision ${this.revision.number}${this.dirty ? ' · modifiée' : ' · enregistrée'}` : 'Nouveau draft';
  }
  async perform(action) {
    if (this.busy) return; this.busy = true; this.error = false; this.status = 'Opération en cours…'; this.render();
    try { await action(); } catch (error) {
      this.error = true; this.status = ({ CONFLICT: 'Conflit : rechargez le draft avant de sauvegarder.', ACTION_DENIED: 'Action ou configuration non autorisée.', UNSUPPORTED_FORMAT: 'XML hors du sous-ensemble P0 : import refusé sans modification.', NOT_FOUND: 'Élément introuvable ou inaccessible.', CAPACITY_EXCEEDED: 'Capacité P0 atteinte ou runtime occupé.' })[error.code] || 'L’opération n’a pas pu être confirmée. Consultez son état avant de relancer un test.';
    } finally { this.busy = false; this.render(); }
  }
  acceptRevision(revision) {
    this.revision = revision; this.definition = structuredClone(revision.definition); this.dirty = false; this.report = null; this.xmlSource = '';
    this.status = `Révision ${revision.number} enregistrée (mémoire du serveur).`;
    this.dispatchEvent(new CustomEvent('studio:revision', { detail: { draftId: revision.draftId, revisionId: revision.revisionId, number: revision.number }, bubbles: true, composed: true }));
  }
  acceptSubmission(value) {
    this.submission = value; this.status = value.state === 'COMPLETED' ? 'Test terminé ; résultat attaché à la révision indiquée.' : `État du test : ${value.state}.`;
    this.dispatchEvent(new CustomEvent('studio:run', { detail: { requestId: value.requestId, state: value.state }, bubbles: true, composed: true }));
  }
  render() {
    if (!this.context) return;
    const root = this.shadowRoot; root.replaceChildren(el('style', styles));
    const toolbar = el('div', undefined, 'toolbar'); const title = el('div'); title.append(el('h2', 'Composer une chaîne'));
    const revisionLabel = el('small'); revisionLabel.dataset.revision = ''; title.append(revisionLabel);
    const actions = el('div', undefined, 'actions');
    actions.append(button('Valider', () => this.perform(async () => { this.report = await this.client.validate(this.definition); this.status = this.report.valid ? 'Définition valide pour ce catalogue.' : 'La validation a détecté des problèmes.'; }), !this.allowed('VALIDATE')),
      button('Enregistrer le draft', () => this.perform(async () => this.acceptRevision(this.revision ? await this.client.save(this.revision.draftId, this.revision.number, this.definition) : await this.client.create(this.definition))), !this.allowed('EDIT'), 'primary'));
    toolbar.append(title, actions); root.append(toolbar, el('div', this.status, `status${this.error ? ' error' : ''}`)); root.querySelector('.status').setAttribute('role', 'status');
    const grid = el('div', undefined, 'grid'); grid.append(this.catalogPanel(), this.chainPanel(), this.testPanel()); root.append(grid);
    const lower = el('div', undefined, 'lower'); lower.append(this.validationPanel(), this.xmlPanel()); root.append(lower);
    root.append(el('p', 'P0 · Stockage temporaire en mémoire · Séquences et conditions GEL · Contrats String · Les tests exécutent réellement les opérations autorisées.', 'notice'));
    this.updateToolbar();
  }
  panel(title, badge, className = '') { const panel = el('section', undefined, `panel ${className}`); const header = el('div', undefined, 'heading'); header.append(el('h2', title)); if (badge) header.append(el('span', badge, 'tag')); panel.append(header); return panel; }
  catalogPanel() {
    const panel = this.panel('Opérations', `${this.catalog.length} disponibles`); const search = field('Rechercher', this.search || '', value => { this.search = value; list.replaceChildren(...this.catalogCards()); });
    search.className = 'pad'; const list = el('div', undefined, 'catalog'); list.append(...this.catalogCards()); panel.append(search, list); return panel;
  }
  catalogCards() {
    return this.catalog.filter(d => `${d.id} ${d.name} ${d.description}`.toLocaleLowerCase().includes((this.search || '').toLocaleLowerCase())).map(d => {
      const card = el('article', undefined, 'catalog-card'); const top = el('div', undefined, 'top'); top.append(el('h3', d.name), button('+', () => { this.definition.nodes.push(createOperation(d)); this.touch(); this.render(); }, !this.allowed('EDIT') || !d.testAllowed || this.definition.nodes.length >= 16, 'small'));
      top.lastChild.setAttribute('aria-label', `Ajouter ${d.name}`); card.append(top, el('small', d.description), el('span', `${d.input.javaType?.split('.').at(-1) || '?'} → ${d.output.javaType?.split('.').at(-1) || '?'}`, 'contract'));
      if (!d.testAllowed) card.append(el('small', 'TEST non autorisé')); return card;
    });
  }
  chainPanel() {
    const panel = this.panel('Définition structurée', 'TEST'); const name = field('Identifiant de la chaîne', this.definition.id, value => { this.definition.id = value; this.touch(); }, { disabled: !this.allowed('EDIT') }); name.className = 'pad'; panel.append(name);
    const canvas = el('div', undefined, 'canvas'); canvas.append(el('div', 'Entrée · String', 'endpoint'));
    if (!this.definition.nodes.length) canvas.append(el('p', 'Ajoutez une opération depuis le catalogue.', 'empty'));
    this.definition.nodes.forEach((node, index) => {
      const wrapper = el('div', undefined, 'node-wrap'); const card = node.kind === 'choice' ? this.choiceCard(node) : this.operationCard(node);
      const tools = el('div', undefined, 'tools'); const disabled = !this.allowed('EDIT');
      tools.append(button('↑', () => { this.definition = moveNode(this.definition, index, -1); this.touch(); this.render(); }, disabled || index === 0), button('↓', () => { this.definition = moveNode(this.definition, index, 1); this.touch(); this.render(); }, disabled || index === this.definition.nodes.length - 1), button('×', () => { this.definition.nodes.splice(index, 1); this.touch(); this.render(); }, disabled));
      ['Monter', 'Descendre', 'Supprimer'].forEach((label, i) => tools.children[i].setAttribute('aria-label', `${label} ${node.id}`));
      card.querySelector('.node-top').append(tools); wrapper.append(card); canvas.append(wrapper);
    });
    canvas.append(el('div', 'Sortie · String', 'endpoint')); panel.append(canvas);
    const foot = el('div', undefined, 'foot'); foot.append(button('+ Condition GEL', () => {
      const descriptor = this.catalog.find(d => d.testAllowed); if (!descriptor) return;
      const id = `choice_${crypto.randomUUID().replaceAll('-', '').slice(0, 8)}`;
      this.definition.nodes.push({ kind: 'choice', id, branchId: `${id}_yes`, condition: 'input == "ELECTRIC"', whenTrue: createOperation(descriptor), whenFalse: createOperation(descriptor) }); this.touch(); this.render();
    }, !this.allowed('EDIT') || this.definition.nodes.length >= 16)); panel.append(foot);
    const resume = el('details'); resume.className = 'pad'; resume.append(el('summary', 'Identité et reprise d’un draft'));
    const body = el('div', undefined, 'pad'); if (this.revision) body.append(el('code', this.revision.draftId));
    body.append(field('Identifiant du draft à charger', this.resumeId || '', value => this.resumeId = value), button('Charger le draft', () => this.perform(async () => this.acceptRevision(await this.client.draft(this.resumeId))), this.busy));
    resume.append(body); panel.append(resume); return panel;
  }
  operationCard(node) {
    const descriptor = this.catalog.find(d => d.id === node.operationId); const card = el('article', undefined, 'node'); const top = el('div', undefined, 'node-top'); const title = el('div', undefined, 'title'); title.append(el('h3', descriptor?.name || node.operationId), el('code', node.id)); top.append(title); card.append(top);
    const params = el('div', undefined, 'params');
    Object.entries(descriptor?.parameters || {}).forEach(([key, parameter]) => {
      const value = node.parameters[key]?.value || ''; const set = v => { node.parameters[key] = { kind: parameter.kind, value: v }; this.touch(); };
      if (parameter.kind === 'RESOURCE_REFERENCE') { const label = el('label', parameter.label); const select = el('select'); parameter.allowedReferences.forEach(ref => { const option = el('option', ref); option.value = ref; select.append(option); }); select.value = value; select.disabled = !this.allowed('EDIT'); select.addEventListener('change', () => set(select.value)); label.append(select); params.append(label); }
      else params.append(field(parameter.label + (parameter.required ? ' *' : ''), value, set, { disabled: !this.allowed('EDIT') }));
    }); card.append(params); return card;
  }
  choiceCard(node) {
    const card = el('article', undefined, 'node choice'); const top = el('div', undefined, 'node-top'); const title = el('div', undefined, 'title'); title.append(el('h3', 'Choix conditionnel'), el('code', node.id)); top.append(title); card.append(top);
    const condition = field('Expression GEL', node.condition, value => { node.condition = value; this.touch(); }, { disabled: !this.allowed('EDIT') }); condition.className = 'params'; card.append(condition);
    const branches = el('div', undefined, 'branches');
    [['whenTrue', 'Si vrai'], ['whenFalse', 'Sinon']].forEach(([key, text]) => {
      const branch = el('div'); branch.append(el('div', text, 'branch-label')); const select = el('select'); select.setAttribute('aria-label', `Opération ${text}`);
      this.catalog.filter(d => d.testAllowed).forEach(d => { const option = el('option', d.name); option.value = d.id; select.append(option); }); select.value = node[key].operationId; select.disabled = !this.allowed('EDIT');
      select.addEventListener('change', () => { node[key] = createOperation(this.catalog.find(d => d.id === select.value), node[key].id); this.touch(); this.render(); }); branch.append(select, this.operationCard(node[key])); branches.append(branch);
    }); card.append(branches); return card;
  }
  testPanel() {
    const panel = this.panel('Test contrôlé', 'Runtime réel', 'test-panel'); const body = el('div', undefined, 'pad'); body.append(el('p', 'Le test utilise une révision enregistrée. Il peut produire les effets décrits dans le catalogue.', 'test-intro'));
    body.append(field('Input de test (synthétique)', this.testInput, value => this.testInput = value, { multiline: true, disabled: !this.allowed('TEST') }));
    const test = button('Exécuter le test', () => this.perform(async () => {
      this.pendingTest = { draftId: this.revision.draftId, revision: this.revision.number, requestId: crypto.randomUUID() };
      this.submission = null; this.render(); this.acceptSubmission(await this.client.test(this.pendingTest.draftId, { ...this.pendingTest, input: this.testInput }));
    }), !this.allowed('TEST') || this.dirty || !this.revision, 'primary'); test.dataset.action = 'test'; body.append(test);
    if (this.pendingTest) { body.append(el('small', `Requête : ${this.pendingTest.requestId}`), button('Actualiser ce test', () => this.perform(async () => this.acceptSubmission(await this.client.testRequest(this.pendingTest.requestId))), this.busy)); }
    if (this.submission) {
      const detail = el('div', undefined, 'run-detail'); const run = this.submission.run;
      detail.append(el('div', run?.outcome || this.submission.state, 'run-status'));
      if (run) {
        const stale = el('small', 'Résultat d’un autre draft ou d’un état antérieur.'); stale.dataset.runStale = ''; stale.hidden = !this.dirty && run.revisionId === this.revision?.revisionId; detail.append(stale);
        detail.append(el('small', `Révision ${run.revision} · ${run.runtimeContext} · mode ${run.mode}`), el('code', `runId : ${run.runId || 'indisponible'}`));
        const steps = el('ul', undefined, 'steps'); run.steps.forEach(s => { const li = el('li'); li.append(el('span', s.nodeId), el('strong', s.status)); steps.append(li); }); detail.append(steps);
        [['Input', run.input], ['Output', run.output]].forEach(([label, capture]) => { const box = el('div', undefined, 'capture'); box.append(el('small', `${label} · ${capture.state}`), el('pre', capture.state === 'NOT_CAPTURED' ? 'Aucune donnée capturée' : capture.value ?? 'null')); detail.append(box); });
        const provenance = el('details'); provenance.append(el('summary', 'Provenance exacte'), el('pre', JSON.stringify({ draftId: run.draftId, revisionId: run.revisionId, catalogFingerprint: run.catalogFingerprint, artifactHash: run.artifactHash, applicationBuild: run.applicationBuild, startedAt: run.startedAt, endedAt: run.endedAt }, null, 2))); detail.append(provenance);
      } else detail.append(el('small', this.submission.errorCode || 'Rechargez cet état ; ne relancez pas automatiquement le test.'));
      body.append(detail);
    } else body.append(el('p', 'Enregistrez votre draft pour tester une révision précise.', 'muted'));
    panel.append(body); return panel;
  }
  validationPanel() {
    const panel = this.panel('Validation', this.report ? (this.report.valid ? 'Valide' : 'À corriger') : 'À vérifier'); const body = el('div', undefined, 'pad'); panel.dataset.validation = '';
    if (!this.report) body.append(el('p', 'Vérifiez la grammaire, les contrats, les paramètres et la compilation Gear4J.', 'muted'));
    else if (this.report.valid && !this.report.diagnostics.length) body.append(el('p', 'Aucune incohérence détectée pour ce catalogue.', 'success'));
    else { const list = el('ul', undefined, 'diagnostics'); this.report.diagnostics.forEach(d => { const item = el('li', d.message); item.append(el('code', `${d.severity} · ${d.code} · ${d.path}`)); list.append(item); }); body.append(list); }
    body.append(el('small', 'Un draft invalide peut être enregistré. Une configuration interdite ne peut pas l’être.')); panel.append(body); return panel;
  }
  xmlPanel() {
    const panel = this.panel('Vue XML', 'Adaptateur'); const body = el('div', undefined, 'pad'); const actions = el('div', undefined, 'actions');
    actions.append(button('Générer le XML', () => this.perform(async () => { this.xmlSource = (await this.client.exportXml(this.definition)).source; this.status = 'XML canonique généré.'; }), this.busy), button('Importer en nouveau draft', () => this.perform(async () => this.acceptRevision(await this.client.importXml(this.xmlSource))), !this.allowed('EDIT')));
    body.append(actions, field('Définition XML', this.xmlSource, value => this.xmlSource = value, { multiline: true, disabled: this.busy, className: 'xml' }), el('small', 'Normalisation sémantique : commentaires et mise en forme non conservés. Les constructions non prises en charge sont refusées.')); panel.append(body); return panel;
  }
}
if (!customElements.get('gear4j-studio')) customElements.define('gear4j-studio', Gear4jStudio);
