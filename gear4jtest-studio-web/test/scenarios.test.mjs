import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ManagementClient } from '../src/management-client.mjs';
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost/' });
for (const name of ['window','document','HTMLElement','customElements','CustomEvent']) globalThis[name] = dom.window[name];
await import('../src/studio-scenarios.mjs');
const find = (e, text) => [...e.shadowRoot.querySelectorAll('button')].find(b => b.textContent === text);
async function settled(e) { for (let i=0; e.busy && i<200; i++) await new Promise(r=>setTimeout(r,1)); assert.equal(e.busy,false); }
function fixture() {
 const calls = []; let dirty = false;
 const state = { scenario: { id:'s1', resourceId:'draft', name:'Greeting', version:1, head:1 }, revision: { id:'r1', number:1, author:'alice', data:{ input:{ kind:'SYNTHETIC_TEXT',value:'<script>hello</script>',fixtureVersion:null }, expected:{ outcome:'SUCCEEDED',output:'HELLO' } } }, actions:['EDIT','ARCHIVE','SELECT_TARGET'] };
 const clone = () => structuredClone(state);
 const client = { scenarios:async()=>({items:[clone()],nextCursor:null}), scenario:async()=>clone(),
  createScenario:async(...args)=>{calls.push(['create',...args]);return clone();},
  saveScenario:async(...args)=>{calls.push(['save',...args]);state.scenario.version++;state.scenario.head++;state.revision.number++;state.revision.data=args[3];return clone();},
  scenarioRevisions:async()=>({items:[clone().revision],nextCursor:null}),
  testScenario:async(id,request)=>{calls.push(['test',id,request]);return {state:'COMPLETED',run:{scenarioCheck:{status:'PASSED',differences:[]}}};},
  testRequest:async id=>{calls.push(['receipt',id]);return {state:'OUTCOME_UNKNOWN'};},
  archiveScenario:async(...args)=>{calls.push(['archive',...args]);state.actions=[];state.scenario.archived=true;return clone();}
 };
 const e=document.createElement('gear4j-scenarios'); document.body.replaceChildren(e);
 return {e,client,calls,state,scopes:[{id:'draft',label:'Private draft',permissions:['READ_SCENARIOS','MANAGE_SCENARIOS']}],getTargets:()=>[{key:'dr1',label:'Draft r1',target:{draftId:'draft',revision:1},disabled:dirty}],dirty:value=>{dirty=value;}};
}
test('scenario client pins revisions and targets without input duplication or caller identity',async()=>{
 const calls=[]; const c=new ManagementClient({fetchImpl:async(url,init)=>{calls.push([url,init.method,init.body&&JSON.parse(init.body)]);return {ok:true,json:async()=>({})};}});
 await c.testScenario('s/1',{revision:2,target:{versionId:'v1'},requestId:'q'});
 await c.scenarioRevisions('s/1','cursor');
 assert.deepEqual(calls[0],['/api/v1/scenarios/s%2F1/tests','POST',{revision:2,target:{versionId:'v1'},requestId:'q'}]);
 assert.equal(calls[1][0],'/api/v1/scenarios/s%2F1/revisions?limit=25&after=cursor');
});
test('reusable component retains unsaved fixtures and rejects dirty authoring targets before POST',async()=>{
 const f=fixture(); const e=f.e; await e.configure(f); await e.perform(()=>e.choose('s1'));
 assert.equal(e.shadowRoot.querySelector('script'),null);
 const input=e.shadowRoot.querySelector('[name=value]'); input.value='world'; input.dispatchEvent(new dom.window.Event('input'));
 find(e,'Tester le scénario').click(); await settled(e); assert.deepEqual(f.calls,[]); assert.equal(e.hasUnsavedChanges,true);
 find(e,'Nouveau scénario').click(); assert.equal(e.selected.scenario.id,'s1'); assert.equal(e.form.value,'world');
 find(e,'Enregistrer le scénario').click(); await settled(e); assert.equal(f.calls[0][0],'save');assert.equal(f.calls[0][2],1);assert.equal(f.calls[0][4].input.value,'world');
 f.dirty(true); find(e,'Tester le scénario').click(); await settled(e);assert.equal(f.calls.length,1);
 f.dirty(false); find(e,'Tester le scénario').click(); await settled(e);assert.equal(f.calls[1][0],'test');assert.equal(f.calls[1][2].revision,2);assert.deepEqual(f.calls[1][2].target,{draftId:'draft',revision:1});assert.match(e.shadowRoot.textContent,/PASSED/);
 find(e,'Révisions du scénario').click();await settled(e);assert.ok(find(e,'Reprendre la révision 2 · alice'));
 find(e,'Confirmer l’archivage').click();await settled(e);assert.equal(find(e,'Tester le scénario'),undefined);
});
test('transport loss never replays a scenario test and receipt lookup is an explicit GET',async()=>{
 const f=fixture(); let posts=0;f.client.testScenario=async()=>{posts++;throw new Error('Synthetic transport interruption');};
 await f.e.configure(f);await f.e.perform(()=>f.e.choose('s1'));find(f.e,'Tester le scénario').click();await settled(f.e);
 assert.equal(posts,1);assert.ok(f.e.pending.requestId);find(f.e,'Relire le reçu du test').click();await settled(f.e);assert.equal(posts,1);assert.equal(f.calls.at(-1)[0],'receipt');assert.match(f.e.shadowRoot.textContent,/OUTCOME_UNKNOWN/);
});
test('read-only scopes expose history but no save controls, and forbidden responses clear data',async()=>{
 const f=fixture();f.scopes[0].permissions=['READ_SCENARIOS'];f.state.actions=[];await f.e.configure(f);await f.e.perform(()=>f.e.choose('s1'));
 assert.equal(find(f.e,'Enregistrer le scénario'),undefined);assert.equal(f.e.shadowRoot.querySelector('[name=value]').disabled,true);
 f.client.scenario=async()=>{throw Object.assign(new Error(),{code:'FORBIDDEN',status:403});};await f.e.perform(()=>f.e.choose('s1'));
 assert.equal(f.e.selected,null);assert.equal(f.e.form.value,'');assert.equal(f.e.items.length,0);
});
