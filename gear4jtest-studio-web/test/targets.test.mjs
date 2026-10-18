import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
const dom = new JSDOM('<!doctype html><body></body>', { url: 'http://localhost/' });
for (const name of ['window', 'document', 'HTMLElement', 'customElements', 'CustomEvent']) globalThis[name] = dom.window[name];
const { FederationClient } = await import('../src/studio-targets.mjs');
const targets = ['java','spring'].map(id => ({ id, applicationId:id, applicationName:`App ${id}`, name:id === 'java' ? '<img src=x>' : 'Spring', runtimeContextId:`runtime.${id}` }));
const context = id => ({context:{id:`runtime.${id}`,capabilities:[]},identity:{principalId:'alice'},permissions:[],canCreateDomain:false});
function fixture() {
  const calls = [];
  const clients = Object.fromEntries(targets.map(t => [t.id, { context: async()=>context(t.id), list:async()=>({items:[],nextCursor:null}) }]));
  const client = {targets:async()=>({identity:'alice',targets}),management:(id,contextId,onUnavailable)=>{calls.push(id);clients[id].unavailable=onUnavailable;return clients[id];}};
  const element = document.createElement('gear4j-targets'); document.body.replaceChildren(element);
  return { element, client, clients, calls };
}
test('federation client binds every call to a target namespace and never supplies an endpoint or identity', async()=>{
  const calls=[]; const client=new FederationClient({tokenProvider:()=> 'token',fetchImpl:async(url,options)=>{calls.push([url,options]);return {ok:true,json:async()=>({targets})};}});
  await client.targets(); await client.management('java','runtime.java').createDomain('catalog','Catalog','');
  assert.equal(calls[0][0],'/studio/api/v1/targets'); assert.equal(calls[1][0],'/targets/java/api/v1/domains');
  assert.deepEqual(JSON.parse(calls[1][1].body),{key:'catalog',name:'Catalog',description:''});
  assert.equal(calls[1][1].headers.Authorization,'Bearer token');
  assert.equal(calls[1][1].headers['X-Gear4J-Expected-Context'],'runtime.java');
  assert.throws(()=>client.management('https://elsewhere')); assert.equal(calls.length,2);
});
test('switching isolates workspaces and preserves dirty fixtures or forms until explicit discard',async()=>{
  const f=fixture(); await f.element.configure(f); await f.element.selectTarget('java');
  assert.equal(f.element.shadowRoot.querySelector('img'),null); const first=f.element.workspace;
  first.formValues.set('creation',{name:'in progress'});
  await f.element.selectTarget('spring'); assert.equal(f.element.workspace,first); assert.equal(f.element.pending,'spring'); assert.deepEqual(f.calls,['java']);
  await f.element.selectTarget('spring',true); assert.notEqual(f.element.workspace,first); assert.equal(first.isConnected,false);
  assert.equal(f.element.selected.id,'spring'); assert.equal(f.element.workspace.formValues.size,0);
  f.element.workspace.scenariosElement={hasUnsavedChanges:true}; await f.element.selectTarget('java'); assert.equal(f.element.pending,'java');
  f.element.workspace.scenariosElement=null; f.element.workspace.busy=true; await f.element.selectTarget('java',true); assert.equal(f.element.selected.id,'spring');
});
test('late target connections cannot replace a newer selected context and mismatches fail closed',async()=>{
  const f=fixture(); await f.element.configure(f); let resolve;
  f.clients.java.context=()=>new Promise(r=>{resolve=r;}); const old=f.element.selectTarget('java');
  await f.element.selectTarget('spring'); resolve(context('java')); await old;
  assert.equal(f.element.selected.id,'spring');
  f.clients.java.context=async()=>context('other'); await f.element.selectTarget('java');
  assert.equal(f.element.selected.id,'spring'); assert.match(f.element.shadowRoot.textContent,/autre contexte/);
  f.clients.spring.unavailable({code:'REMOTE_ACCESS_DENIED'}); assert.equal(f.element.workspace,null);
});
test('revocation and logout clear displayed data and stale inventory responses are discarded',async()=>{
  const f=fixture(); await f.element.configure(f); await f.element.selectTarget('java');
  f.clients.java.unavailable({code:'REMOTE_ACCESS_DENIED'}); assert.equal(f.element.workspace,null); assert.equal(f.element.content.children.length,0);
  await f.element.selectTarget('java'); f.client.targets=async()=>({identity:'alice',targets:[]}); await f.element.refresh(); assert.equal(f.element.workspace,null);
  let resolve; f.client.targets=()=>new Promise(r=>{resolve=r;}); const stale=f.element.refresh();
  await f.element.configure({client:{...f.client,targets:async()=>({identity:'bob',targets:[]})}}); resolve({identity:'alice',targets}); await stale;
  assert.deepEqual(f.element.targets,[]); assert.equal(f.element.identity,'bob');
  f.element.disconnect(); assert.equal(f.element.client,null); assert.equal(f.element.identity,'');
});
test('remote failures are surfaced without retrying a mutation',async()=>{
  let calls=0, cleared=0;
  const c=new FederationClient({fetchImpl:async()=>{calls++;return {ok:false,status:502,json:async()=>({code:'REMOTE_OUTCOME_UNKNOWN'})};}}).management('java','runtime.java',()=>cleared++);
  await assert.rejects(c.publishTest('version'),error=>error.code==='REMOTE_OUTCOME_UNKNOWN');
  assert.equal(calls,1); assert.equal(cleared,0);
});
