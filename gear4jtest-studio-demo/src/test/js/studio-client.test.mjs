import test from 'node:test';
import assert from 'node:assert/strict';
import { StudioClient, createOperation, moveNode } from '../../main/resources/studio/studio-client.mjs';
test('host authentication and conditional revision travel in the correct request', async () => {
 let observed;
 const client = new StudioClient({ baseUrl: 'http://localhost:8787/', tokenProvider: () => 'host-token', fetchImpl: async (url, init) => { observed = {url, init}; return {ok:true,json:async()=>({number:2})}; } });
 assert.deepEqual(await client.save('draft',1,{id:'sample'}),{number:2});
 assert.equal(observed.url,'http://localhost:8787/api/drafts/draft'); assert.equal(observed.init.headers.Authorization,'Bearer host-token');
 assert.deepEqual(JSON.parse(observed.init.body),{expectedRevision:1,definition:{id:'sample'}}); assert.equal(observed.init.credentials,'omit');
});
test('a lost test response never causes an automatic POST retry', async () => {
 let calls=0; const client=new StudioClient({fetchImpl:async()=>{calls++;throw new Error('network');}});
 await assert.rejects(client.test('draft',{requestId:'same-key',revision:1,input:'synthetic'}));assert.equal(calls,1);
});
test('receipt recovery is a GET for the original identity', async()=>{
 let observed;const client=new StudioClient({fetchImpl:async(url,init)=>{observed={url,init};return {ok:true,json:async()=>({state:'COMPLETED'})};}});
 await client.testRequest('request'); assert.equal(observed.url,'/api/tests/request');assert.equal(observed.init.method,'GET');assert.equal(observed.init.body,undefined);
});
test('server exception details are not surfaced in client errors',async()=>{
 const client=new StudioClient({fetchImpl:async()=>({ok:false,status:409,json:async()=>({code:'CONFLICT',message:'sensitive'})})});
 await assert.rejects(client.save('d',1,{}),error=>error.code==='CONFLICT'&&error.status===409&&!error.message.includes('sensitive'));
});
test('reordering is immutable and preserves stable node identities',()=>{
 const definition={nodes:[{id:'first'},{id:'second'}]};assert.deepEqual(moveNode(definition,0,1).nodes.map(n=>n.id),['second','first']);assert.equal(definition.nodes[0].id,'first');
});
test('resource parameters are references rather than literal secret fields',()=>{
 const node=createOperation({id:'op',parameters:{resource:{kind:'RESOURCE_REFERENCE',allowedReferences:['approved/ref']}}},'step');assert.deepEqual(node.parameters.resource,{kind:'RESOURCE_REFERENCE',value:'approved/ref'});
});
