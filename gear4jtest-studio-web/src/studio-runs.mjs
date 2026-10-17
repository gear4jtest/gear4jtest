const style = `:host{display:block;margin-top:20px;color:#203f3b;font:14px/1.5 system-ui}*{box-sizing:border-box}.box{background:white;border:1px solid #d9e3df;border-radius:10px;padding:18px;margin:12px 0}.bar,.filters{display:flex;flex-wrap:wrap;gap:10px;align-items:end}label{display:grid;gap:4px;font-size:12px}input,select,button{font:inherit;padding:7px;border:1px solid #b8ccc4;border-radius:5px;background:white;color:inherit}button{cursor:pointer}button:disabled{opacity:.5;cursor:default}table{width:100%;border-collapse:collapse;font-size:12px}th,td{padding:8px;text-align:left;border-bottom:1px solid #e1e9e5;vertical-align:top;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;background:#f5f8f6;padding:12px}code{font-size:11px;overflow-wrap:anywhere}.muted{color:#667c77}.error{color:#a63728}.stats{display:flex;gap:16px;flex-wrap:wrap}.stat{min-width:115px;padding:12px;background:#eff6f2;border-radius:8px}.stat strong{display:block;font-size:24px}.steps td:first-child{max-width:300px}meter{width:100%}h3,h4{margin:0 0 10px}button:focus-visible{outline:3px solid #006b60}@media(max-width:700px){.filters label{width:100%}table{font-size:11px}}`;
const el = (tag, text, cls) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };
const button = (text, action, disabled = false) => { const node = el('button', text); node.type = 'button'; node.disabled = disabled; node.addEventListener('click', action); return node; };
const active = run => run && ['RUNNING', 'PREPARING'].includes(run.status);
const duration = (start, end) => start && end ? `${Math.max(0, Date.parse(end) - Date.parse(start))} ms` : '—';
function table(headers, rows) { const node = el('table'); const head = el('tr'); headers.forEach(v => head.append(el('th', v))); const thead = el('thead'); thead.append(head); const body = el('tbody'); rows.forEach(values => { const row = el('tr'); values.forEach(value => { const cell = el('td'); cell.append(value instanceof Node ? value : document.createTextNode(String(value ?? '—'))); row.append(cell); }); body.append(row); }); node.append(thead, body); return node; }

/** Public exploitation component. No action here executes, publishes or retries a chain. */
export class Gear4jRuns extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this.generation = 0; this.auto = false; this.items = []; }
  async configure({ client, scopeId, canReadRuns = true, canReadObservability = false }) {
    this.stop(); this.generation++; this.loading = null; this.client = client; this.scopeId = scopeId; this.canReadRuns = canReadRuns; this.canReadObservability = canReadObservability;
    const now = new Date(); const tomorrow = new Date(now); tomorrow.setUTCHours(24, 0, 0, 0);
    this.filter = { from: new Date(now.getTime() - 86400000).toISOString(), until: tomorrow.toISOString() };
    this.pageAfter = null; this.pendingId = null; this.selected = null; this.metrics = null; this.next = null; this.streamUnavailable = false; this.items = []; this.build(); await this.reload();
  }
  connectedCallback() { this.schedule(); }
  disconnectedCallback() { this.stop(); this.generation++; }
  stop() { clearTimeout(this.timer); this.stream?.abort(); this.stream = null; }
  schedule() { clearTimeout(this.timer); if (this.auto && this.isConnected) this.timer = setTimeout(() => this.tick(), 2000); }
  async tick() {
    await this.reload(this.pageAfter);
    if (this.selected && !this.stream) await this.select(this.selected.id, false);
    this.schedule();
  }
  build() {
    const root = this.shadowRoot; root.replaceChildren(el('style', style));
    const box = el('section', null, 'box'); box.append(el('h3', 'Exploitation'), el('p', 'Historique durable des observations accessibles. TEST et RUN restent distincts.'));
    const form = el('form', null, 'filters'); const inputs = {};
    for (const [key, label] of [['from', 'Début (UTC)'], ['until', 'Fin (UTC)'], ['status', 'Statut'], ['mode', 'Mode'], ['versionId', 'Version Studio (UUID)'], ['runId', 'Run Gear4J (UUID)'], ['minimumDurationMillis', 'Durée minimale (ms)']]) {
      const field = el('label', label); let input;
      if (key === 'status' || key === 'mode') {
        input = el('select'); for (const value of key === 'mode' ? ['', 'TEST', 'RUN'] : ['', 'PREPARING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'STOPPED', 'SKIPPED', 'PREPARATION_FAILED', 'UNKNOWN']) { const option = el('option', value || 'Tous'); option.value = value; input.append(option); }
      } else { input = el('input'); input.type = key === 'from' || key === 'until' ? 'datetime-local' : key === 'minimumDurationMillis' ? 'number' : 'text'; input.maxLength = 64; if (input.type === 'number') input.min = '0'; }
      input.name = key; if (this.filter[key]) input.value = this.filter[key].slice(0,16); field.append(input); inputs[key] = input; form.append(field);
    }
    const submit = el('button', 'Appliquer les filtres'); submit.type = 'submit'; form.append(submit);
    form.addEventListener('submit', event => { event.preventDefault(); const filter = {}; for (const [key, input] of Object.entries(inputs)) if (input.value) filter[key] = key === 'from' || key === 'until' ? `${input.value}:00Z` : input.value; this.filter = filter; this.selected = null; this.pendingId = null; this.stop(); this.generation++; this.loading = null; this.pageAfter = null; this.renderDetail(); this.reload(); });
    const bar = el('div', null, 'bar'); this.autoBox = el('input'); this.autoBox.type = 'checkbox'; this.autoBox.checked = this.auto;
    const follow = el('label', 'Actualiser automatiquement'); follow.prepend(this.autoBox); this.autoBox.addEventListener('change', () => { this.auto = this.autoBox.checked; if (!this.auto) this.stop(); else { this.schedule(); if (active(this.selected)) this.watch(); } });
    bar.append(button('Actualiser', () => this.reload(this.pageAfter)), follow); this.message = el('p', '', 'muted'); this.message.setAttribute('role','status'); this.results = el('div'); this.detail = el('div');
    box.append(form, bar, this.message, this.results, this.detail); root.append(box);
  }
  failure(error) {
    if ([401,403,404].includes(error.status)) { this.items = []; this.selected = null; this.metrics = null; this.auto = false; this.autoBox.checked = false; this.stop(); }
    this.message.textContent = [401,403,404].includes(error.status) ? 'Accès indisponible. Les données affichées ont été retirées.' : 'Observation indisponible. Actualisez pour relire l’état durable.';
    this.message.className = 'error'; this.renderResults(); this.renderDetail();
  }
  async reload(after = null) {
    if (this.loading) return; const loading = this.loading = {}; const generation = this.generation;
    try {
      const page = this.canReadRuns ? await this.client.runs(this.scopeId, this.filter, after) : { items: [], nextCursor: null };
      const metrics = this.canReadObservability ? await this.client.metrics(this.scopeId, this.filter) : null;
      if (generation !== this.generation) return;
      this.items = page.items; this.next = page.nextCursor; this.pageAfter = after; this.metrics = metrics;
      this.message.textContent = `Dernière lecture : ${new Date().toLocaleTimeString()}.`; this.message.className = 'muted'; this.renderResults();
    } catch (error) { if (generation === this.generation) this.failure(error); }
    finally { if (this.loading === loading) { this.loading = null; this.schedule(); } }
  }
  async select(id, restart = true) {
    if (restart) { this.stream?.abort(); this.stream = null; this.streamUnavailable = false; }
    const generation = this.generation; const selectedId = this.pendingId = id;
    try {
      const run = await this.client.run(id); if (generation !== this.generation || this.pendingId !== selectedId) return;
      this.selected = run; this.renderDetail(); this.dispatchEvent(new CustomEvent('studio:run', { detail: { id: run.id, status: run.status }, bubbles: true, composed: true }));
      if (this.auto && active(run) && !this.streamUnavailable) this.watch();
    } catch (error) { if (generation === this.generation && this.pendingId === selectedId) this.failure(error); }
  }
  async watch() {
    if (!this.client.watchRun || this.stream || !active(this.selected)) return;
    const controller = this.stream = new AbortController(); const id = this.selected.id; const generation = this.generation;
    try {
      await this.client.watchRun(id, snapshot => {
        if (generation !== this.generation || this.selected?.id !== id || controller.signal.aborted) return;
        this.selected = snapshot; this.renderDetail();
      }, { signal: controller.signal });
    } catch (error) {
      if (!controller.signal.aborted && generation === this.generation) {
        this.streamUnavailable = true;
        if ([401,403,404].includes(error.status)) this.failure(error);
        else { this.selected = null; this.renderDetail(); this.message.textContent = 'Flux indisponible : relecture périodique des snapshots.'; this.pendingId = id; }
      }
    } finally {
      if (this.stream === controller) this.stream = null;
      if (!controller.signal.aborted && generation === this.generation && this.auto) {
        try { const snapshot = await this.client.run(id); if (!controller.signal.aborted && generation === this.generation && this.pendingId === id) { this.selected = snapshot; this.renderDetail(); } } catch (error) { if (!controller.signal.aborted && generation === this.generation && this.pendingId === id) this.failure(error); }
        this.schedule();
      }
    }
  }
  renderResults() {
    this.results.replaceChildren();
    if (this.metrics) {
      const m = this.metrics; const box = el('section', null, 'box'); box.append(el('h4', m.partial ? 'Indicateurs partiels — parcours ou capture des étapes limités' : 'Indicateurs des observations accessibles'));
      const stats = el('div', null, 'stats'); for (const [label,value] of [['Runs',m.total],['Succès',m.statuses.SUCCEEDED || 0],['Échecs',m.statuses.FAILED || 0],['p95',m.p95Millis == null ? '—' : `${m.p95Millis} ms`]]) { const stat = el('div',label,'stat'); stat.append(el('strong',value)); stats.append(stat); } box.append(stats);
      box.append(el('p', `Durée moyenne : ${m.averageMillis == null ? '—' : `${Math.round(m.averageMillis)} ms`}. Les résultats sans durée finale sont exclus des durées.`), table(['Statut','Nombre'],Object.entries(m.statuses)));
      if (m.timeline.length) box.append(table(['Période (UTC)','Runs','Échecs'], m.timeline.map(b => [b.from,b.runs,b.failures])));
      if (m.operations.length) box.append(el('h4','Stations observées par version, mode et artefact'), table(['Étape','Contexte','Occurrences','Échecs','Moyenne','Maximum'],m.operations.map(o=>[o.nodeId,JSON.stringify(o.dimension || {}),o.executions,o.failures,o.averageMillis == null ? '—' : `${Math.round(o.averageMillis)} ms`,o.maximumMillis == null ? '—' : `${o.maximumMillis} ms`])));
      this.results.append(box);
    }
    if (!this.canReadRuns) return;
    this.results.append(table(['Exécution','Statut','Mode / origine','Version / révision','Début / durée'], this.items.map(r => [button(r.id, () => this.select(r.id)),`${r.status}${r.staleAt ? ' · observation ancienne' : ''}`,`${r.provenance.mode} · ${r.origin}`,r.provenance.versionLabel || (r.provenance.revisionId ? `Révision ${r.provenance.revision}` : 'Non attestée'),`${r.startedAt || r.createdAt} · ${duration(r.startedAt,r.endedAt)}`])));
    if (!this.items.length) this.results.append(el('p', this.next ? 'Aucun run visible dans cette portion. Continuez le parcours.' : 'Aucune observation accessible dans cette période.'));
    const pages = el('div',null,'bar'); pages.append(button('Début de l’historique',()=>this.reload(),!this.pageAfter),button('Runs suivants',()=>this.reload(this.next),!this.next)); this.results.append(pages);
  }
  renderDetail() {
    this.detail.replaceChildren(); const run = this.selected; if (!run) return;
    const box = el('section',null,'box'); box.append(el('h4',`Exécution ${run.id}`),el('p',`${run.status} · dernière observation ${run.updatedAt} · ${duration(run.startedAt,run.endedAt)}`));
    if (run.staleAt) box.append(el('p', `Observation signalée ancienne depuis ${run.staleAt}. Le résultat métier reste inconnu.`, 'error'));
    if (active(run)) box.append(el('p','Statut observé, sans garantie de présence du processus. Une interruption peut laisser une observation sans résultat final.','muted'));
    const provenance = el('details'); provenance.append(el('summary','Provenance exacte'),el('pre',JSON.stringify(run.provenance,null,2))); box.append(provenance);
    const observation = run.observation;
    if (!observation) box.append(el('p','Aucun détail disponible ou droit READ_OBSERVABILITY absent.'));
    else {
      box.append(el('code',`Gear4J : ${observation.runId || 'non démarré'} · parent : ${observation.parentRunId || '—'} · racine : ${observation.rootRunId || '—'}`));
      for (const [name,value] of [['Entrée',observation.input],['Sortie',observation.output]]) { box.append(el('h4',`${name} · ${value.state}`)); if (value.state !== 'NOT_CAPTURED') box.append(el('pre',value.value ?? 'null')); }
      if (observation.scenarioCheck) box.append(el('p', `Scénario : ${observation.scenarioCheck.status} · ${observation.scenarioCheck.differences.join(', ')}`));
      if (observation.errorCode) box.append(el('p',`Erreur : ${observation.errorCode}`,'error'));
      if (observation.stepsTruncated) box.append(el('p','Détail partiel : la limite de capture des étapes a été atteinte.','error'));
      const byId = new Map(observation.steps.map(s=>[s.id,s]));
      box.append(table(['Étape / branche','Statut','Durée','Erreur'],observation.steps.map(s=>{
        let depth=0, parent=s.parentId; const seen=new Set([s.id]); while(parent && byId.has(parent) && !seen.has(parent) && depth<8){seen.add(parent);depth++;parent=byId.get(parent).parentId;}
        const label=el('span',`${s.nodeId}${s.branchId ? ` · ${s.branchId}` : ''}`);label.style.paddingLeft=`${depth*14}px`;label.title=`Étape ${s.id} · run ${s.runId || observation.runId || '—'}`;
        return [label,s.status,duration(s.startedAt,s.endedAt),s.errorCode];
      })));
      box.append(el('p','Les inputs/outputs par étape, messages d’exception et logs libres ne sont pas capturés par cet adaptateur.','muted'));
    }
    this.detail.append(box);
  }
}
if (!customElements.get('gear4j-runs')) customElements.define('gear4j-runs', Gear4jRuns);
