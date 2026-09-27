// Start both demos with GEAR4J_STUDIO_TOKEN=synthetic-editor-token-0123456789
// and GEAR4J_STUDIO_CAPTURE=synthetic. This scenario runs real, local test operations.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch();
 try {
  const page = await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  for(const port of [8787,8788]) {
   for(const path of ['/','/embedded']) {
    await page.goto(`http://127.0.0.1:${port}${path}`);
    await page.getByLabel('Token d’accès local').fill('synthetic-editor-token-0123456789');
    await page.getByRole('button',{name:'Ouvrir le Studio'}).click();
    await page.getByRole('heading',{name:'Composer une chaîne'}).waitFor();
    await page.getByRole('button',{name:'Valider',exact:true}).click();
    await page.getByText('Aucune incohérence détectée pour ce catalogue.').waitFor();
    await page.getByRole('button',{name:'Enregistrer le draft'}).click();
    await page.getByText('Révision 1 · enregistrée').waitFor();
    await page.getByRole('button',{name:'Exécuter le test'}).click();
    await page.getByText('SUCCEEDED',{exact:true}).waitFor();
    assert.equal(await page.locator('.capture pre').last().innerText(),'ELECTRIC');
    await page.getByRole('button',{name:'Générer le XML'}).click();
    await page.waitForFunction(()=>document.querySelector('gear4j-studio').shadowRoot.querySelector('textarea.xml')?.value.includes('<assemblyLine'));
    assert.match(await page.getByLabel('Définition XML').inputValue(),/text.uppercase/);
    if(path==='/embedded') await page.getByText('Portail hôte : test COMPLETED.').waitFor();
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);
    await page.setViewportSize({width:1440,height:1000});
   }
  }
  assert.deepEqual(errors,[]);console.log('Plain Java, Spring, embedding, XML, controlled test and responsive-width checks passed.');
 } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exit(1)});
