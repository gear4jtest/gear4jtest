import { createOperation, moveNode } from './studio-client.mjs';
const styles = `table{width:100%;border-collapse:collapse;font-size:12px}td,th{text-align:left;padding:7px;border-bottom:1px solid #d9e3df;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:11px} .node:focus{outline:2px solid #006b60}
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
  async configure({ client, revision = null }) {
    clearTimeout(this.diagnosticsTimer); this.diagnosticsGeneration = (this.diagnosticsGeneration || 0) + 1; this.xmlEdited = false; this.xmlPreview = null; this.xmlOriginal = null; this.comparison = null;
    const [context, catalog] = await Promise.all([client.context(), client.catalog()]);
    this.client = client; this.context = context; this.catalog = catalog; this.definition = structuredClone(context.sample);
    this.revision = null; this.dirty = true; this.report = null; this.submission = null; this.pendingTest = null; this.xmlSource = ''; this.status = 'Catalogue de l’application connecté.'; this.error = false; if (revision) this.acceptRevision(revision); this.render(); return context;
  }
  allowed(action) { return this.context.actions.includes(action) && !this.busy && !(action === 'TEST' && (this.xmlEdited || !this.testPayloadSupported())); }
  testPayloadSupported() { return !this.context.context.capabilities?.includes('CONTROLLED_TEST_STRING') || this.definition.inputType === 'java.lang.String' && this.definition.outputType === 'java.lang.String'; }
  get hasUnsavedChanges() { return this.dirty || this.xmlEdited; }
  touch() { this.diagnosticsGeneration = (this.diagnosticsGeneration || 0) + 1; clearTimeout(this.diagnosticsTimer); this.comparison = null; this.xmlPreview = null; this.dirty = true; this.report = null; this.xmlSource = ''; this.error = false; this.status = 'Modifications locales — enregistrez une révision avant de tester.'; this.updateToolbar();
    this.shadowRoot.querySelector('[data-validation]')?.replaceWith(this.validationPanel());
    const xml = this.shadowRoot.querySelector('textarea.xml'); if (xml) xml.value = '';
    const stale = this.shadowRoot.querySelector('[data-run-stale]'); if (stale) stale.hidden = false;
    if (this.allowed('VALIDATE') && this.client.diagnose) { const generation = this.diagnosticsGeneration; const snapshot = structuredClone(this.definition); this.diagnosticsTimer = setTimeout(async () => { try { const report = await this.client.diagnose(snapshot); if (generation !== this.diagnosticsGeneration) return; this.report = { ...report, compiled: false }; this.shadowRoot.querySelector('[data-validation]')?.replaceWith(this.validationPanel()); } catch { /* Explicit validation can be retried; editing remains local. */ } }, 600); }
  }
  updateToolbar() {
    const grid = this.shadowRoot.querySelector('.grid'); if (grid) grid.inert = Boolean(this.xmlEdited);
    const validate = [...this.shadowRoot.querySelectorAll('button')].find(b => b.textContent === 'Valider'); if (validate) validate.disabled = !this.allowed('VALIDATE') || Boolean(this.xmlEdited);
    const save = [...this.shadowRoot.querySelectorAll('button')].find(b => b.textContent === 'Enregistrer le draft'); if (save) save.disabled = !this.allowed('EDIT') || Boolean(this.xmlEdited);
    const status = this.shadowRoot.querySelector('.status'); if (status) status.textContent = this.status;
    const test = this.shadowRoot.querySelector('[data-action="test"]'); if (test) test.disabled = !this.allowed('TEST') || this.dirty || !this.revision;
    const badge = this.shadowRoot.querySelector('[data-revision]'); if (badge) badge.textContent = this.revision ? `Révision ${this.revision.number}${this.dirty ? ' · modifiée' : ' · enregistrée'}` : 'Nouveau draft';
  }
  async perform(action) {
    if (this.busy) return; this.busy = true; this.error = false; this.status = 'Opération en cours…'; this.render();
    try { await action(); } catch (error) {
      this.error = true; this.status = ({ INVALID_DEFINITION: 'Définition refusée : vérifiez notamment son identifiant Gear4J immuable.', CONFLICT: 'Conflit : rechargez le draft avant de sauvegarder.', ACTION_DENIED: 'Action ou configuration non autorisée.', FORBIDDEN: 'Vos droits ne permettent pas cette action. Rechargez les droits du draft.', UNAUTHENTICATED: 'La session n’est plus authentifiée.', STORAGE_UNAVAILABLE: 'Le stockage est indisponible. Rechargez avant de réessayer.', UNSUPPORTED_FORMAT: 'Ce XML contient une construction non prise en charge.', NOT_FOUND: 'Élément introuvable ou inaccessible.', CAPACITY_EXCEEDED: 'Capacité de test atteinte ou service occupé.' })[error.code] || 'L’opération n’a pas pu être confirmée. Consultez son état avant de relancer un test.';
    } finally { this.busy = false; this.render(); }
  }
  acceptRevision(revision) {
    if (revision.actions) this.context.actions = [...revision.actions, ...(this.context.actions.includes('IMPORT') ? ['IMPORT'] : [])];
    this.revision = revision; this.definition = structuredClone(revision.definition); this.dirty = false; this.report = null; this.xmlSource = ''; this.xmlEdited = false; this.xmlPreview = null; this.comparison = null;
    this.status = `Révision ${revision.number} enregistrée${this.context.persistent ? '' : ' (mémoire du serveur)'}.`;
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
    actions.append(button('Valider', () => this.perform(async () => { clearTimeout(this.diagnosticsTimer); this.diagnosticsGeneration = (this.diagnosticsGeneration || 0) + 1; this.report = { ...await this.client.validate(this.definition), compiled: true }; this.status = this.report.valid ? this.report.diagnostics.length ? 'Validation avec avertissements : vérifiez les incertitudes.' : 'Définition valide pour ce catalogue.' : 'La validation a détecté des problèmes.'; }), !this.allowed('VALIDATE')),
      button('Enregistrer le draft', () => this.perform(async () => this.acceptRevision(this.xmlEdited ? (() => { throw { code: 'INVALID_DEFINITION' }; })() : this.revision ? await this.client.save(this.revision.draftId, this.revision.draftVersion ?? this.revision.number, this.definition) : await this.client.create(this.definition))), !this.allowed('EDIT'), 'primary'));
    toolbar.append(title, actions); root.append(toolbar, el('div', this.status, `status${this.error ? ' error' : ''}`)); root.querySelector('.status').setAttribute('role', 'status');
    const grid = el('div', undefined, 'grid'); grid.append(this.catalogPanel(), this.chainPanel(), this.testPanel()); root.append(grid);
    const lower = el('div', undefined, 'lower'); lower.append(this.validationPanel(), this.xmlPanel()); root.append(lower);
    if (this.revision) root.append(button('Comparer avec la révision enregistrée', () => this.perform(async () => { this.comparison = await this.client.diff(this.definition); }), this.busy || !this.client.diff)); if (this.comparison) root.append(comparisonPanel(this.comparison));
    root.append(el('p', 'Séquences et choix GEL · Contrats du catalogue · Les tests exécutent réellement les opérations autorisées.', 'notice'));
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
      card.append(el('small', `IN : ${d.input.origin || 'DECLARED'} · OUT : ${d.output.origin || 'DECLARED'}`)); const contracts = el('details'); contracts.append(el('summary', 'Contrats et effets'), el('p', d.effects), el('pre', JSON.stringify({ input: d.input, output: d.output }, null, 2))); card.append(contracts);
      if (!d.testAllowed) card.append(el('small', 'TEST non autorisé')); return card;
    });
  }
  chainPanel() {
    const panel = this.panel('Définition structurée', 'TEST'); const name = field('Identifiant Gear4J (immuable)', this.definition.id, value => { this.definition.id = value; this.touch(); }, { disabled: true }); name.className = 'pad'; panel.append(name);
    for (const [key, label] of [['inputType', 'Contrat d’entrée'], ['outputType', 'Contrat de sortie']]) { const row = el('label', label, 'pad'); const select = el('select'); const types = [...new Set(['java.lang.String', this.definition[key], ...this.catalog.flatMap(d => [d.input.javaType, d.output.javaType]).filter(Boolean)])]; for (const type of types) { const option = el('option', type); option.value = type; select.append(option); } select.value = this.definition[key]; select.disabled = !this.allowed('EDIT'); select.addEventListener('change', () => { this.definition[key] = select.value; this.touch(); this.render(); }); row.dataset.path = `/${key}`; row.append(select); panel.append(row); }
    const canvas = el('div', undefined, 'canvas'); canvas.append(el('div', `Entrée · ${this.definition.inputType}`, 'endpoint'));
    if (!this.definition.nodes.length) canvas.append(el('p', 'Ajoutez une opération depuis le catalogue.', 'empty'));
    this.definition.nodes.forEach((node, index) => {
      const wrapper = el('div', undefined, 'node-wrap'); const card = node.kind === 'choice' ? this.choiceCard(node, `/nodes/${index}`) : this.operationCard(node, `/nodes/${index}`);
      const tools = el('div', undefined, 'tools'); const disabled = !this.allowed('EDIT');
      tools.append(button('↑', () => { this.definition = moveNode(this.definition, index, -1); this.touch(); this.render(); }, disabled || index === 0), button('↓', () => { this.definition = moveNode(this.definition, index, 1); this.touch(); this.render(); }, disabled || index === this.definition.nodes.length - 1), button('×', () => { this.definition.nodes.splice(index, 1); this.touch(); this.render(); }, disabled));
      ['Monter', 'Descendre', 'Supprimer'].forEach((label, i) => tools.children[i].setAttribute('aria-label', `${label} ${node.id}`));
      card.querySelector('.node-top').append(tools); wrapper.append(card); canvas.append(wrapper);
    });
    canvas.append(el('div', `Sortie · ${this.definition.outputType}`, 'endpoint')); panel.append(canvas);
    const foot = el('div', undefined, 'foot'); foot.append(button('+ Condition GEL', () => {
      const descriptor = this.catalog.find(d => d.testAllowed && d.input.javaType === 'java.lang.String' && d.output.javaType === 'java.lang.String' && Object.values(d.parameters).every(p => !p.required)) || this.catalog.find(d => d.testAllowed); if (!descriptor) return;
      const id = `choice_${crypto.randomUUID().replaceAll('-', '').slice(0, 8)}`;
      this.definition.nodes.push({ kind: 'choice', id, branchId: `${id}_yes`, condition: 'input == "ELECTRIC"', whenTrue: createOperation(descriptor), whenFalse: createOperation(descriptor) }); this.touch(); this.render();
    }, !this.allowed('EDIT') || this.definition.nodes.length >= 16)); panel.append(foot);
    const resume = el('details'); resume.className = 'pad'; resume.append(el('summary', 'Identité et reprise d’un draft'));
    const body = el('div', undefined, 'pad'); if (this.revision) body.append(el('code', this.revision.draftId));
    body.append(field('Identifiant du draft à charger', this.resumeId || '', value => this.resumeId = value), button('Charger le draft', () => this.perform(async () => this.acceptRevision(await this.client.draft(this.resumeId))), this.busy));
    resume.append(body); if (!this.context.persistent) panel.append(resume); return panel;
  }
  operationCard(node, path) {
    const descriptor = this.catalog.find(d => d.id === node.operationId); const card = el('article', undefined, 'node'); const top = el('div', undefined, 'node-top'); const title = el('div', undefined, 'title'); title.append(el('h3', descriptor?.name || node.operationId), el('code', node.id)); top.append(title); card.append(top);
    card.dataset.path = path; card.tabIndex = -1; const params = el('div', undefined, 'params');
    const select = el('select'); select.setAttribute('aria-label', `Opération ${node.id}`); for (const descriptor of this.catalog) { const option = el('option', descriptor.name); option.value = descriptor.id; select.append(option); } select.value = node.operationId; select.disabled = !this.allowed('EDIT'); select.addEventListener('change', () => { Object.assign(node, createOperation(this.catalog.find(d => d.id === select.value), node.id)); this.touch(); this.render(); }); params.append(select);
    Object.entries(descriptor?.parameters || {}).forEach(([key, parameter]) => {
      const value = node.parameters[key]?.value || ''; const set = v => { node.parameters[key] = { kind: parameter.kind, value: v }; this.touch(); };
      if (parameter.kind === 'RESOURCE_REFERENCE') { const label = el('label', parameter.label); const select = el('select'); parameter.allowedReferences.forEach(ref => { const option = el('option', ref); option.value = ref; select.append(option); }); select.value = value; select.disabled = !this.allowed('EDIT'); select.addEventListener('change', () => set(select.value)); label.append(select); params.append(label); }
      else { const parameterField = field(parameter.label + (parameter.required ? ' *' : ''), value, set, { disabled: !this.allowed('EDIT') }); parameterField.dataset.path = `${path}/parameters/${key.replaceAll('~', '~0').replaceAll('/', '~1')}`; params.append(parameterField); }
    }); card.append(params); return card;
  }
  choiceCard(node, path) {
    const card = el('article', undefined, 'node choice'); const top = el('div', undefined, 'node-top'); const title = el('div', undefined, 'title'); title.append(el('h3', 'Choix conditionnel'), el('code', node.id)); top.append(title); card.append(top);
    card.dataset.path = path; card.tabIndex = -1; const condition = field('Expression GEL', node.condition, value => { node.condition = value; this.touch(); }, { disabled: !this.allowed('EDIT') }); condition.className = 'params'; condition.dataset.path = `${path}/condition`; card.append(condition);
    const branches = el('div', undefined, 'branches');
    [['whenTrue', 'Si vrai'], ['whenFalse', 'Sinon']].forEach(([key, text]) => {
      const branch = el('div'); branch.append(el('div', text, 'branch-label')); const select = el('select'); select.setAttribute('aria-label', `Opération ${text}`);
      this.catalog.filter(d => d.testAllowed).forEach(d => { const option = el('option', d.name); option.value = d.id; select.append(option); }); select.value = node[key].operationId; select.disabled = !this.allowed('EDIT');
      select.addEventListener('change', () => { node[key] = createOperation(this.catalog.find(d => d.id === select.value), node[key].id); this.touch(); this.render(); }); branch.append(select, this.operationCard(node[key], `${path}/${key}`)); branches.append(branch);
    }); card.append(branches); return card;
  }
  testPanel() {
    const panel = this.panel('Test contrôlé', 'Runtime réel', 'test-panel'); const body = el('div', undefined, 'pad'); body.append(el('p', 'Le test utilise une révision enregistrée. Il peut produire les effets décrits dans le catalogue.', 'test-intro'));
    if (!this.testPayloadSupported()) body.append(el('p', 'Ce runtime ne propose pas encore de test pour ces types d’entrée/sortie. La conception et la validation restent disponibles.'));
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
    const panel = this.panel('Validation', this.report ? (this.report.valid ? (this.report.diagnostics.length ? 'Avec réserves' : this.report.compiled ? 'Validé' : 'Analyse') : 'À corriger') : 'À vérifier'); const body = el('div', undefined, 'pad'); panel.dataset.validation = '';
    if (!this.report) body.append(el('p', 'Vérifiez la grammaire, les contrats, les paramètres et la compilation Gear4J.', 'muted'));
    else if (this.report.valid && !this.report.diagnostics.length) body.append(el('p', this.report.compiled ? 'Aucune incohérence détectée pour ce catalogue.' : 'Analyse cohérente. La compilation sera vérifiée avec Valider.', 'success'));
    else { const list = el('ul', undefined, 'diagnostics'); this.report.diagnostics.forEach(d => { const item = el('li', d.message); item.append(el('code', `${d.severity} · ${d.code} · ${d.path}`)); item.append(button('Localiser', () => { const candidates = [...this.shadowRoot.querySelectorAll('[data-path]')].filter(n => d.path === n.dataset.path || d.path.startsWith(`${n.dataset.path}/`)).sort((a,b) => b.dataset.path.length - a.dataset.path.length); const target = candidates[0]; (target?.querySelector('input,textarea,select') || target)?.focus(); target?.scrollIntoView?.({ block: 'center', behavior: 'smooth' }); })); list.append(item); }); body.append(list); }
    body.append(el('small', 'Un draft invalide peut être enregistré. Une configuration interdite ne peut pas l’être.')); panel.append(body); return panel;
  }
  xmlPanel() {
    const panel = this.panel('Vue XML', 'Adaptateur'); const body = el('div', undefined, 'pad'); const actions = el('div', undefined, 'actions');
    actions.append(button('Générer le XML', () => this.perform(async () => { if (this.xmlEdited) { this.status = 'Appliquez ou abandonnez le XML saisi avant de régénérer.'; return; } this.xmlSource = (await this.client.exportXml(this.definition)).source; this.status = 'XML canonique généré.'; }), this.busy),
      button('Prévisualiser le XML', () => this.perform(async () => { this.xmlPreview = await this.client.previewXml(this.xmlSource); this.status = 'Prévisualisation prête : appliquez-la pour remplacer le modèle local.'; }), !this.allowed('EDIT') || !this.client.previewXml));
    if (this.xmlPreview) actions.append(button('Appliquer au draft courant', () => {
      const preview = this.xmlPreview; if (!preview) return; this.xmlOriginal = this.xmlSource; this.definition = structuredClone(preview.definition); this.xmlEdited = false; this.touch(); this.xmlSource = preview.source; this.status = 'XML normalisé appliqué localement ; enregistrez pour créer une révision.'; this.render();
    }, !this.allowed('EDIT')));
    if (this.xmlEdited) actions.append(button('Abandonner la saisie XML', () => { this.xmlEdited = false; this.xmlSource = ''; this.xmlPreview = null; this.render(); }, this.busy));
    body.append(actions, field('Définition XML', this.xmlSource, value => { this.xmlSource = value; this.xmlEdited = true; this.xmlPreview = null; const apply = [...this.shadowRoot.querySelectorAll('button')].find(b => b.textContent === 'Appliquer au draft courant'); if (apply) apply.disabled = true; this.shadowRoot.querySelector('[data-xml-preview]')?.remove(); this.updateToolbar(); }, { multiline: true, disabled: !this.allowed('EDIT'), className: 'xml' }), el('small', 'Round-trip sémantique : commentaires et mise en forme normalisés. Les constructions non prises en charge sont refusées.'));
    if (this.xmlPreview) { const preview = el('details'); preview.dataset.xmlPreview = ''; preview.append(el('summary', 'XML après normalisation'), el('pre', this.xmlPreview.source)); body.append(preview); }
    if (this.xmlOriginal) { const original = el('details'); original.append(el('summary', 'Source XML avant normalisation (locale)'), el('pre', this.xmlOriginal)); body.append(original); }
    panel.append(body); return panel;
  }

}
if (!customElements.get('gear4j-studio')) customElements.define('gear4j-studio', Gear4jStudio);

/** Public render helper used by both workspace history and the standalone editor. */
export function comparisonPanel(comparison) {
  const panel = el('section', undefined, 'panel'); panel.append(el('h3', 'Comparaison des définitions'));
  if (comparison.diff.ambiguousIdentity) panel.append(el('p', 'Identifiants dupliqués : comparaison par position, correspondances ambiguës.'));
  if (!comparison.diff.changes.length) panel.append(el('p', 'Aucun changement sémantique.'));
  const table = el('table'); const head = el('tr'); for (const label of ['Changement', 'Élément', 'Avant', 'Après']) head.append(el('th', label)); table.append(head);
  for (const change of comparison.diff.changes) { const row = el('tr'); for (const value of [change.kind, `${change.nodeId || ''} ${change.path}`, change.before ?? '—', change.after ?? '—']) row.append(el('td', value)); table.append(row); } panel.append(table);
  const xml = el('details'); xml.append(el('summary', 'Comparer les XML canoniques'), el('h4', 'Avant'), el('pre', comparison.baseXml ?? 'XML indisponible pour cette définition.'), el('h4', 'Après'), el('pre', comparison.proposedXml ?? 'XML indisponible pour cette définition.')); panel.append(xml); return panel;
}
