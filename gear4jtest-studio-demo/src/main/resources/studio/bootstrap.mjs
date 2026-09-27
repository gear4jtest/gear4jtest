import { StudioClient } from './studio-client.mjs';
import './studio-element.mjs';
const form = document.querySelector('#connect');
form.addEventListener('submit', async event => {
  event.preventDefault(); const token = form.querySelector('input').value; const message = document.querySelector('#connection-state');
  form.querySelector('button').disabled = true; message.textContent = 'Connexion…';
  try {
    const context = await document.querySelector('gear4j-studio').configure({ client: new StudioClient({ tokenProvider: () => token }) });
    form.querySelector('input').value = ''; form.hidden = true; message.textContent = `${context.context.id} · ${context.actions.includes('EDIT') ? 'Édition autorisée' : 'Consultation'}`;
  } catch { message.textContent = 'Connexion refusée. Vérifiez le token configuré au démarrage.'; }
  finally { form.querySelector('button').disabled = false; }
});
for (const name of ['studio:revision', 'studio:run']) document.addEventListener(name, event => {
  const host = document.querySelector('#host-events');
  if (host) host.textContent = name === 'studio:revision' ? `Portail hôte : révision ${event.detail.number} enregistrée.` : `Portail hôte : test ${event.detail.state}.`;
});
