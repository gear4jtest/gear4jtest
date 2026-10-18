import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ManagementClient } from '../src/management-client.mjs';
const dom = new JSDOM('<!doctype html><body></body>', {url:'http://localhost/'});
for(const name of ['window','document','HTMLElement','customElements','CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-simulation.mjs');
await import('../src/studio-scenarios.mjs');
const button = (e, text) => [...e.shadowRoot.querySelectorAll('button')].find(b=>b.textContent===text);
async function settled(e) { for(let i=0;e.busy&&i<200;i++) await new Promise(r=>setTimeout(r,1)); assert.equal(e.busy,false); }
function fixture() {
 const e=document.createElement('gear4j-simulation');document.body.replaceChildren(e);const calls=[];let dirty=false;
 const profile={schemaVersion:1,mocks:{step:{capability:'text.trim',behavior:'RETURN',type:'STRING',value:'  hello  '}}};
 const report={id:'sim1',scenario:{revision:2},target:{draftId:'draft1',revision:4},result:{outcome:'SUCCEEDED',check:{status:'PASSED'},steps:[{nodeId:'step',capability:'text.trim',status:'SUBSTITUTED',input:{state:'NOT_CAPTURED'},output:{state:'CAPTURED',value:'<script>payload</script>'}}]}};
 const client={planSimulation:async(id,r)=>{calls.push(['plan',id,r]);return {valid:true,diagnostics:[],substitutions:[]};},simulate:async(id,r)=>{calls.push(['simulate',id,r]);return structuredClone(report);},simulation:async id=>{calls.push(['read',id]);return structuredClone(report);},simulations:async()=>({items:[report],nextCursor:null})};
 const options={client,scenarioId:'scenario1',revision:2,profile,editable:true,getTargets:()=>[{key:'target1',label:'Draft r4',target:{draftId:'draft1',revision:4},definition:{nodes:[{id:'step',operationId:'text.trim'}]}}],unsaved:()=>dirty,onChange:p=>{options.profile=p;dirty=true;}};
 e.configure(options);return {e,options,calls,dirty:v=>{dirty=v;}};
}
test('public simulation client uses federable paths with pinned revisions and no value duplication',async()=>{
 const calls=[];const client=new ManagementClient({fetchImpl:async(url,init)=>{calls.push([url,init.method,init.body&&JSON.parse(init.body)]);return {ok:true,json:async()=>({})};}});
 await client.planSimulation('s/1',{revision:2,target:{draftId:'d',revision:4}});
 await client.simulate('s/1',{revision:2,target:{versionId:'v'},requestId:'q'});
 await client.simulation('q');await client.simulations('s/1','cursor');
 assert.equal(calls[0][0],'/api/v1/scenarios/s%2F1/simulation-plan');assert.equal(calls[1][0],'/api/v1/scenarios/s%2F1/simulations');
 assert.deepEqual(calls[1][2],{revision:2,target:{versionId:'v'},requestId:'q'});assert.equal(calls[2][0],'/api/v1/simulations/q');assert.match(calls[3][0],/after=cursor/);
});
test('simulation requires saved profile, keeps exact mock text and rereads a report without executing',async()=>{
 const f=fixture();const input=f.e.shadowRoot.querySelector('textarea');input.value='  changed\n';input.dispatchEvent(new dom.window.Event('input'));
 assert.equal(f.options.profile.mocks.step.value,'  changed\n');button(f.e,'Simuler les substitutions').click();await settled(f.e);assert.deepEqual(f.calls,[]);
 f.dirty(false);button(f.e,'Analyser le plan').click();await settled(f.e);assert.equal(f.calls[0][0],'plan');
 button(f.e,'Simuler les substitutions').click();await settled(f.e);assert.equal(f.calls[1][0],'simulate');assert.equal(f.calls[1][2].revision,2);assert.deepEqual(f.calls[1][2].target,{draftId:'draft1',revision:4});
 assert.equal(f.e.shadowRoot.querySelector('script'),null);assert.match(f.e.shadowRoot.textContent,/Résultat simulé : SUCCEEDED/);
 button(f.e,'Relire le rapport').click();await settled(f.e);assert.equal(f.calls[2][0],'read');
 f.options.client.simulation=async()=>{throw Object.assign(new Error(),{status:403,code:'FORBIDDEN'});};button(f.e,'Relire le rapport').click();await settled(f.e);assert.equal(f.e.report,null);assert.doesNotMatch(f.e.shadowRoot.textContent,/payload/);
});
test('profile initialization explicitly substitutes both branches with FAIL and never rewrites an existing mock',()=>{
 const f=fixture();f.options.getTargets=()=>[{key:'target1',target:{draftId:'d',revision:4},definition:{nodes:[{id:'step',operationId:'text.trim'},{id:'choice',whenTrue:{id:'yes',operationId:'text.trim'},whenFalse:{id:'no',operationId:'text.uppercase'}}]}}];f.e.configure(f.options);
 button(f.e,'Préparer les mocks des nœuds').click();assert.equal(f.options.profile.mocks.step.value,'  hello  ');assert.equal(f.options.profile.mocks.yes.behavior,'FAIL');assert.equal(f.options.profile.mocks.no.behavior,'FAIL');
});
test('scenario saves the reusable profile and preserves it when other fixture fields change',async()=>{
 const e=document.createElement('gear4j-scenarios');let saved;
 const profile={schemaVersion:1,mocks:{step:{capability:'text.trim',behavior:'RETURN',type:'STRING',value:'  hello  '}}};
 const state={scenario:{id:'s',name:'S',version:1,head:1},revision:{number:1,data:{input:{kind:'SYNTHETIC_TEXT',value:'hello'},simulation:profile}},actions:['EDIT','SIMULATE_TARGET']};
 const client={scenarios:async()=>({items:[state]}),scenario:async()=>state,saveScenario:async(id,v,name,data)=>{saved=data;return {...state,revision:{...state.revision,data}};}};
 await e.configure({client,scopes:[{id:'d',label:'Draft',permissions:['READ_SCENARIOS','MANAGE_SCENARIOS']}],simulationEnabled:true,getTargets:()=>[]});
 await e.perform(()=>e.choose('s'));e.form.name='Changed';await e.perform(()=>e.save());assert.deepEqual(saved.simulation,profile);
 assert.ok(e.shadowRoot.querySelector('gear4j-simulation'));
});

test('historical drift warnings remain visible on plan and persisted report read without a new simulation',async()=>{
 const f=fixture();
 const warning={severity:'WARNING',code:'HISTORICAL_CONTEXT_DIFFERS',path:'/context/catalogFingerprint',message:'Le catalogue historique diffère du catalogue simulé.'};
 f.options.client.planSimulation=async()=>({valid:true,diagnostics:[warning],substitutions:[]});
 button(f.e,'Analyser le plan').click();await settled(f.e);
 assert.match(f.e.shadowRoot.textContent,/Plan valide pour la simulation/);
 assert.match(f.e.shadowRoot.textContent,/Le catalogue historique diffère/);
 f.options.client.simulation=async()=>({id:'historical',diagnostics:[warning],result:{outcome:'SUCCEEDED',check:{status:'PASSED'},steps:[]}});
 f.e.plan=null;f.e.pending={requestId:'historical'};f.e.render();
 button(f.e,'Relire le rapport').click();await settled(f.e);
 assert.match(f.e.shadowRoot.textContent,/WARNING · \/context\/catalogFingerprint/);
 assert.match(f.e.shadowRoot.textContent,/ne reproduit pas son exécution historique/);
 assert.equal(f.calls.filter(c=>c[0]==='simulate').length,0);
});
