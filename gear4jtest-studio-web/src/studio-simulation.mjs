const el = (tag, text) => { const node = document.createElement(tag); if (text != null) node.textContent = text; return node; };
const button = (text, action, disabled = false) => { const node = el('button', text); node.type = 'button'; node.disabled = disabled; node.addEventListener('click', action); return node; };
const css = `:host{min-width:0;max-width:100%}section,.form,label,.box{min-width:0}input,select,textarea,button{min-width:0;max-width:100%;box-sizing:border-box}button{overflow-wrap:anywhere}table{table-layout:fixed}:host{display:block;color:#203f3b;font:14px/1.5 system-ui}*{box-sizing:border-box}section{display:grid;gap:12px;border:1px solid #b8ccc4;border-radius:8px;padding:14px;background:#f7faf8}h4,p{margin:0}.bar{display:flex;gap:8px;flex-wrap:wrap}label{display:grid;gap:4px;font-size:12px}input,select,textarea,button{font:inherit;border:1px solid #b8ccc4;border-radius:5px;padding:7px;background:white;color:inherit}button{cursor:pointer}button:disabled{opacity:.5;cursor:default}.mock{display:grid;grid-template-columns:1fr 1fr;gap:8px;border-top:1px solid #d9e3df;padding-top:10px}textarea{min-height:50px;grid-column:1/-1;width:100%}table{border-collapse:collapse;width:100%;font-size:12px}th,td{text-align:left;padding:7px;border-bottom:1px solid #d9e3df;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:11px}button:focus-visible{outline:3px solid #006b60}@media(max-width:600px){.mock{grid-template-columns:1fr}table{display:block;overflow-x:auto}}`;
/** Public isolated simulation component. Profiles belong to the host scenario revision. */
export class Gear4jSimulation extends HTMLElement {
  constructor() { super(); this.attachShadow({mode:'open'}); this.message = ''; }
  configure(options) {
    const key = `${options.scenarioId || ''}:${options.revision || ''}`;
    if (key !== this.key) { this.plan = null; this.report = null; this.history = null; this.pending = null; this.message = ''; }
    this.key = key; Object.assign(this, options); this.render();
  }
  changed(profile) { this.profile = profile; this.plan = null; this.onChange?.(structuredClone(profile)); this.render(); }
  get target() { return this.getTargets().find(t => t.key === this.targetKey); }
  async perform(work) {
    if (this.busy) return; this.busy = true; this.message = 'Simulation en cours…'; this.render();
    try { await work(); } catch (error) {
      this.message = ({INVALID_DEFINITION:'Le plan est invalide. Analysez les diagnostics avant de simuler.', CONFLICT:'Cette requête désigne déjà une autre simulation.', FORBIDDEN:'Simulation non autorisée.', NOT_FOUND:'Rapport inaccessible.', SCENARIO_DATA_NOT_ADMITTED:'Données refusées par la politique de l’application.'})[error.code] || 'Résultat non confirmé. Relisez le rapport avec son identifiant avant de recommencer.';
      if ([401,403,404].includes(error.status)) { this.report = null; this.history = null; this.plan = null; this.onUnavailable?.(); }
    } finally { this.busy = false; this.render(); }
  }
  request() {
    if (!this.scenarioId || this.unsaved() || !this.target || this.target.disabled) { this.message = 'Enregistrez le scénario et choisissez une cible structurée enregistrée.'; return null; }
    return {revision:this.revision, target:structuredClone(this.target.target)};
  }
  async analyze() { const request = this.request(); if (!request) return; this.plan = await this.client.planSimulation(this.scenarioId, request); this.message = this.plan.valid ? 'Plan complet. Aucune opération métier exécutée.' : 'Plan incomplet ou incompatible.'; }
  async simulate() {
    const request = this.request(); if (!request) return;
    this.pending = {...request, requestId:crypto.randomUUID()}; this.report = null;
    this.report = await this.client.simulate(this.scenarioId, this.pending); this.message = 'Rapport de simulation enregistré. Ce résultat ne valide pas le comportement métier.';
    this.dispatchEvent(new CustomEvent('studio:simulation', {detail:{id:this.report.id},bubbles:true,composed:true}));
  }
  initialize() {
    const definition = this.target?.definition; if (!definition) return;
    const mocks = structuredClone(this.profile?.mocks || {});
    for (const node of definition.nodes) for (const op of node.kind === 'choice' || node.whenTrue ? [node.whenTrue,node.whenFalse] : [node]) {
      if (!mocks[op.id]) mocks[op.id] = {capability:op.operationId,behavior:'FAIL',type:null,value:null};
    }
    this.changed({schemaVersion:1,mocks});
  }
  render() {
    if (!this.client) return;
    const root = this.shadowRoot; root.replaceChildren(el('style', css)); const box = el('section'); box.append(el('h4','Simulation isolée'));
    box.append(el('p','Toutes les opérations sont substituées. Les conditions du modèle sont évaluées sur les données synthétiques. Aucune ressource métier ni fixture externe n’est résolue. Ces mocks s’appliquent uniquement à la simulation ; le test contrôlé exécute les opérations réelles. Une version publiée est simulée avec le catalogue et le simulateur actuels ; cela ne reproduit pas son exécution historique.'));
    const status = el('p',this.message); status.setAttribute('role','status'); box.append(status);
    const targets = this.getTargets(); if (!targets.some(t => t.key === this.targetKey)) this.targetKey = targets[0]?.key;
    const targetLabel = el('label','Cible exacte de la simulation'); const target = el('select');
    for (const t of targets) { const o = el('option', t.label); o.value = t.key; o.disabled = !!t.disabled; target.append(o); } target.value = this.targetKey || ''; target.disabled = this.busy;
    target.addEventListener('change',()=>{this.targetKey=target.value;this.plan=null;this.render();}); targetLabel.append(target);box.append(targetLabel);
    const blocked = !this.editable || this.busy;
    if (this.editable) {
      const controls = el('div');controls.className='bar';controls.append(button('Préparer les mocks des nœuds',()=>this.initialize(),blocked || !this.target?.definition), button('Ajouter un mock',()=>{let i=1;while(this.profile?.mocks[`node_${i}`])i++;this.changed({schemaVersion:1,mocks:{...this.profile?.mocks,[`node_${i}`]:{capability:'',behavior:'FAIL',type:null,value:null}}});},blocked || Object.keys(this.profile?.mocks||{}).length>=32));box.append(controls);
      box.append(el('small','Chaque mock doit être configuré puis enregistré avec le scénario. Le comportement initial FAIL est explicite ; aucun appel réel ne sert de repli.'));
    }
    for (const [nodeId,mock] of Object.entries(this.profile?.mocks || {})) {
      const row=el('div');row.className='mock';
      const edit=(label,value,change,choices=null)=>{const wrap=el('label',label);const input=el(choices?'select':'input');if(choices)for(const c of choices){const o=el('option',c);o.value=c;input.append(o);}input.value=value??'';input.disabled=blocked;input.addEventListener('change',()=>change(input.value));wrap.append(input);row.append(wrap);};
      const update=(fields)=>this.changed({schemaVersion:1,mocks:{...this.profile.mocks,[nodeId]:{...mock,...fields}}});
      edit('Nœud',nodeId,value=>{if(!/^[a-zA-Z_][a-zA-Z0-9_]{0,63}$/.test(value)||value!==nodeId&&this.profile.mocks[value]){this.message='Identifiant de nœud invalide ou déjà utilisé.';this.render();return;}const mocks={...this.profile.mocks};delete mocks[nodeId];mocks[value]=mock;this.changed({schemaVersion:1,mocks});});
      edit('Capability',mock.capability,value=>update({capability:value}));
      edit('Comportement',mock.behavior,value=>update({behavior:value,type:value==='RETURN'?'STRING':null,value:value==='RETURN'?'':null}),['RETURN','PASSTHROUGH','FAIL']);
      if(mock.behavior==='RETURN') {
        edit('Type de sortie',mock.type,value=>update({type:value}),['STRING','INTEGER','LONG','DECIMAL','BOOLEAN']);
        const wrap=el('label','Valeur synthétique exacte');const input=el('textarea');input.value=mock.value??'';input.maxLength=8192;input.disabled=blocked;
        input.addEventListener('input',()=>{const profile={schemaVersion:1,mocks:{...this.profile.mocks,[nodeId]:{...this.profile.mocks[nodeId],value:input.value}}};this.profile=profile;this.plan=null;this.onChange?.(structuredClone(profile));});wrap.append(input);row.append(wrap);
      }
      if(this.editable)row.append(button('Retirer ce mock',()=>{const mocks={...this.profile.mocks};delete mocks[nodeId];this.changed({schemaVersion:1,mocks});},blocked));box.append(row);
    }
    const controls=el('div');controls.className='bar';controls.append(button('Analyser le plan',()=>this.perform(()=>this.analyze()),this.busy||!this.scenarioId),button('Simuler les substitutions',()=>this.perform(()=>this.simulate()),this.busy||!this.scenarioId));
    if(this.scenarioId)controls.append(button('Rapports enregistrés',()=>this.perform(async()=>{this.history=await this.client.simulations(this.scenarioId);this.message='Rapports accessibles pour ce scénario.';}),this.busy));box.append(controls);
    if(this.pending){box.append(el('code',`Simulation ${this.pending.requestId}`));box.append(button('Relire le rapport',()=>this.perform(async()=>{this.report=await this.client.simulation(this.pending.requestId);this.message='Rapport relu sans nouvelle simulation.';}),this.busy));}
    if(this.plan){box.append(el('strong',this.plan.valid?'Plan valide pour la simulation':'Plan refusé'));for(const d of this.plan.diagnostics)box.append(el('p',`${d.severity} · ${d.path} · ${d.message}`));for(const s of this.plan.substitutions)box.append(el('small',`${s.nodeId} · ${s.capability} · ${s.behavior}`));}
    if(this.history){for(const r of this.history.items)box.append(button(`Simulation ${r.id} · ${r.result.outcome}`,()=>this.perform(async()=>{this.report=await this.client.simulation(r.id);}),this.busy));if(this.history.nextCursor)box.append(button('Rapports suivants',()=>this.perform(async()=>{this.history=await this.client.simulations(this.scenarioId,this.history.nextCursor);}),this.busy));}
    if(this.report){const result=this.report.result;box.append(el('strong',`Résultat simulé : ${result.outcome} · assertions ${result.check.status}`));for(const d of this.report.diagnostics||[])box.append(el('p',`${d.severity} · ${d.path} · ${d.message}`));if(result.failureCode)box.append(el('p',result.failureCode));const table=el('table');const head=el('tr');for(const name of ['Nœud / capability','État simulé','Entrée','Sortie'])head.append(el('th',name));table.append(head);
      const capture=v=>v?.state==='NOT_CAPTURED'?'Non capturé':`${v?.state||''} : ${v?.value??''}`;
      for(const step of result.steps){const row=el('tr');for(const value of [`${step.nodeId} ${step.capability||''}`,`${step.status}${step.selectedNodeId?' → '+step.selectedNodeId:''}`,capture(step.input),capture(step.output)])row.append(el('td',value));table.append(row);}box.append(table);
      const details=el('details');details.append(el('summary','Provenance du rapport'),el('pre',JSON.stringify({...this.report,result:{engineVersion:result.engineVersion,catalogFingerprint:result.catalogFingerprint}},null,2)));box.append(details);
    }
    root.append(box);
  }
}
if (!customElements.get('gear4j-simulation')) customElements.define('gear4j-simulation', Gear4jSimulation);
