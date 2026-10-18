import { FederationClient } from './studio-targets.mjs';
const form = document.querySelector('#connect');
const message = document.querySelector('#connection-state');
document.addEventListener('studio:disconnect', () => { form.hidden = false; message.textContent = 'Déconnecté.'; });
form.addEventListener('submit', async event => {
  event.preventDefault(); const token = form.querySelector('input').value;
  form.querySelector('button').disabled = true;
  try {
    await document.querySelector('gear4j-targets').configure({ client: new FederationClient({ tokenProvider: () => token }) });
    form.querySelector('input').value = ''; form.hidden = true; message.textContent = 'Choisissez une application et une cible.';
  } catch { message.textContent = 'Connexion refusée ou Studio indisponible.'; }
  finally { form.querySelector('button').disabled = false; }
});
