import './studio-versions.mjs';
import { comparisonPanel } from './studio-element.mjs';
import { ScopedAuthoringClient } from './management-client.mjs';
const css = `table{table-layout:fixed;width:100%;border-collapse:collapse;font-size:12px}td,th{text-align:left;vertical-align:top;padding:7px;border-bottom:1px solid #d9e3df;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:11px}:host{display:block;color:#203f3b;font:14px/1.5 system-ui,sans-serif}*{box-sizing:border-box}button,input,select,textarea{font:inherit}button{cursor:pointer;padding:7px 11px;border:1px solid #c6d8ce;background:white;border-radius:6px;color:#205044}button:hover:enabled{background:#edf6f2}button:disabled{opacity:.45;cursor:default}input,select,textarea{width:100%;padding:7px;border:1px solid #b8ccc4;border-radius:5px}label{display:grid;gap:4px;color:#526d62;font-size:12px}h2,h3,p{margin:0}small{color:#667c77}code{font-size:11px;overflow-wrap:anywhere}.layout{display:grid;grid-template-columns:260px minmax(0,1fr);gap:18px;margin:18px 0}.box{background:white;border:1px solid #d9e3df;border-radius:10px;padding:15px;display:grid;gap:12px;align-content:start}.bar{display:flex;gap:7px;align-items:center;flex-wrap:wrap}.list{display:grid;gap:7px}.list button{text-align:left}.list small{display:block}.message{min-height:28px;margin:10px 0}.error{color:#a63728}.forms{display:grid;grid-template-columns:1fr 1fr;gap:9px}.wide{grid-column:1/-1}details{border-top:1px solid #e2eae5;padding-top:10px}summary{cursor:pointer;font-weight:600;margin-bottom:9px}.grants{display:grid;gap:12px}.grant{padding:10px;background:#f5f8f6;display:grid;gap:9px;border:1px solid #d9e3df;border-radius:6px}.checks{display:flex;gap:9px;flex-wrap:wrap}.checks label{display:flex;align-items:center}.checks input{width:auto}.audit{font-size:12px;border-bottom:1px solid #e2eae5;padding:8px 0;overflow-wrap:anywhere}.audit pre{white-space:pre-wrap;font-size:11px}.selected{border-color:#006b60;background:#edf6f2}.intro{display:grid;gap:6px}.muted{color:#667c77}a{color:#006b60}@media(max-width:800px){.layout{grid-template-columns:1fr}.forms{grid-template-columns:1fr}.wide{grid-column:auto}}`;
const el = (tag, text, cls) => { const value = document.createElement(tag); if (text !== undefined) value.textContent = text; if (cls) value.className = cls; return value; };
const btn = (text, action, disabled = false) => { const value = el('button', text); value.type = 'button'; value.disabled = disabled; value.addEventListener('click', action); return value; };
function field(text, value = '', type = 'input') { const label = el('label', text); const input = el(type); input.value = value; label.append(input); return { label, input }; }
const labels = { domains: 'Domaines', chains: 'Chaînes', drafts: 'Drafts' };
/** Public workspace component. Uses the public management client and the public authoring component. */
export class Gear4jWorkspace extends HTMLElement {
  constructor() { super(); this.attachShadow({ mode: 'open' }); this.kind = 'domains'; this.items = []; this.message = ''; this.editor = null; this.formValues = new Map(); }
  async configure({ client }) {
    this.client = client; this.context = await client.context(); this.selected = null; this.editor = null; this.kind = 'domains'; this.parent = null; this.cursor = null; this.formValues.clear(); this.versions = null;
    await this.reload(); return this.context;
  }
  async reload(after = null) {
    const page = await this.client.list(this.kind, { parent: this.parent, after });
    this.items = page.items; this.next = page.nextCursor; this.cursor = after; this.render();
  }
  async perform(work) {
    if (this.busy) return; this.busy = true; this.message = 'Opération en cours…'; this.error = false; this.render();
    try { await work(); } catch (error) {
      this.error = true; this.message = ({ CONFLICT: 'Conflit : actualisez la ressource avant de réessayer. Vos saisies ne sont pas envoyées à nouveau.', FORBIDDEN: 'Action non autorisée.', NOT_FOUND: 'Ressource introuvable ou inaccessible.', INVALID_REQUEST: 'Vérifiez les champs et les permissions.', STORAGE_UNAVAILABLE: 'Stockage indisponible : vérifiez l’état avant de recommencer.' })[error.code] || 'L’opération n’a pas pu être confirmée.';
    } finally { this.busy = false; this.render(); }
  }
  allowed(permission) { return !this.busy && this.selected?.permissions.includes(permission); }
  async browse(kind, parent = null) { this.kind = kind; this.parent = parent; await this.reload(); }
  async choose(item, discard = false) {
    if (this.editor?.hasUnsavedChanges && !discard) { this.pending = item; this.message = 'Le draft contient des modifications locales. Enregistrez-les ou confirmez leur abandon pour changer de sélection.'; this.render(); return; }
    let selected, editor = null, versions = null;
    if (item.kind === 'DRAFT') {
      const state = await this.client.draft(item.id); selected = { resource: state.draft, kind: 'DRAFT', permissions: state.permissions };
      const scoped = new ScopedAuthoringClient(this.client, state, this.context, saved => {
        this.selected = { resource: saved.draft, kind: 'DRAFT', permissions: saved.permissions }; this.history = null; this.comparison = null; this.accessState = null; this.auditPage = null; this.render();
      });
      editor = document.createElement('gear4j-studio'); await editor.configure({ client: scoped, revision: scoped.flat(state) });
      this.canFork = editor.context.actions.includes('IMPORT');
    } else selected = await this.client.resource(item.id);
    if (this.client.versions && this.context.context.capabilities?.includes('PUBLISH_TEST')) {
      let chain = selected.kind === 'CHAIN' ? selected : null;
      if (selected.kind === 'DRAFT') { try { chain = await this.client.resource(selected.resource.chainId); } catch { /* Draft access need not grant parent access. */ } }
      if (chain?.permissions.includes('READ')) {
        versions = document.createElement('gear4j-versions');
        await versions.configure({ client: this.client, chainId: chain.resource.id,
          canFreeze: chain.permissions.includes('PUBLISH'),
          getDraft: () => editor ? { revision: editor.revision, dirty: editor.hasUnsavedChanges } : null });
        versions.contextFingerprint = this.context.context.catalogFingerprint;
        editor?.addEventListener('studio:revision', () => versions.render());
      }
    }
    this.versions = versions;
    this.pending = null; this.accessState = null; this.auditPage = null; this.history = null; this.comparison = null;
    this.selected = selected; this.editor = editor; this.message = ''; this.render();
  }
  render() {
    if (!this.context) return;
    const root = this.shadowRoot; root.replaceChildren(el('style', css));
    const intro = el('div', undefined, 'intro'); intro.append(el('h2', 'Espace de travail'), el('small', `${this.context.identity.subject} · ${this.context.context.id} · stockage durable`)); root.append(intro);
    const message = el('div', this.message, `message${this.error ? ' error' : ''}`); message.setAttribute('role', 'status'); root.append(message);
    if (this.pending) { const warning = el('div', undefined, 'bar'); warning.append(btn('Abandonner les modifications locales et ouvrir', () => this.perform(() => this.choose(this.pending, true))), btn('Rester sur le draft', () => { this.pending = null; this.message = ''; this.render(); })); root.append(warning); }
    const layout = el('div', undefined, 'layout'); layout.append(this.navigation(), this.details()); root.append(layout);
    if (this.editor) root.append(this.editor);
    if (this.versions) root.append(this.versions);
  }
  navigation() {
    const box = el('section', undefined, 'box'); const tabs = el('div', undefined, 'bar');
    for (const [kind, label] of Object.entries(labels)) tabs.append(btn(label, () => this.perform(() => this.browse(kind)), this.busy));
    box.append(tabs, el('h3', `${labels[this.kind]} accessibles`));
    if (this.parent) box.append(el('small', 'Liste limitée à la sélection parente'), btn('Afficher tous les éléments accessibles', () => this.perform(() => this.browse(this.kind)), this.busy));
    const list = el('div', undefined, 'list');
    for (const item of this.items) { const button = btn(item.name, () => this.perform(() => this.choose(item)), this.busy || !item.readable); button.className = this.selected?.resource.id === item.id ? 'selected' : ''; if (!item.readable) button.append(el('small', 'Existence visible · consultation non autorisée')); list.append(button); }
    if (!this.items.length) list.append(el('p', this.next ? 'Aucun élément visible dans cette portion. Continuez le parcours.' : 'Aucun élément accessible dans cette liste.', 'muted')); box.append(list);
    const pages = el('div', undefined, 'bar'); pages.append(btn('Début', () => this.perform(() => this.reload()), this.busy || !this.cursor), btn('Suivants', () => this.perform(() => this.reload(this.next)), this.busy || !this.next)); box.append(pages);
    if (this.context.canCreateDomain) box.append(this.creation('domain'));
    const open = el('details'); open.append(el('summary', 'Ouvrir un draft par son identifiant')); const id = field('Identifiant du draft'); open.append(id.label, btn('Ouvrir le draft', () => this.perform(() => this.choose({ id: id.input.value, kind: 'DRAFT' })), this.busy)); box.append(open);
    return box;
  }
  remember(key, fields) {
    const saved = this.formValues.get(key) || {};
    for (const [name, field] of Object.entries(fields)) {
      if (Object.hasOwn(saved, name)) field.input.value = saved[name];
      field.input.addEventListener('input', () => { saved[name] = field.input.value; this.formValues.set(key, saved); });
    }
  }
  creation(type) {
    const details = el('details'); const title = type === 'domain' ? 'Créer un domaine' : type === 'chain' ? 'Créer une chaîne' : 'Créer un draft'; details.append(el('summary', title));
    const form = el('form', undefined, 'forms'); const name = field('Nom'); name.input.required = true; name.input.maxLength = 160; const key = field('Clé (minuscules et tirets)'); key.input.required = true; key.input.pattern = '[a-z][a-z0-9-]{0,63}'; const description = field('Description', '', 'textarea'); description.input.maxLength = 2048; description.label.className = 'wide';
    const formKey = `create:${type}:${type === 'domain' ? '' : this.selected?.resource.id}`; const runtimeId = field('Identifiant Gear4J (immuable)', `chain_${crypto.randomUUID().replaceAll('-', '')}`); runtimeId.input.pattern = '[a-zA-Z_][a-zA-Z0-9_]{0,63}'; this.remember(formKey, { name, key, description, runtimeId });
    if (type === 'chain') form.append(runtimeId.label); if (type !== 'draft') form.append(key.label); form.append(name.label); if (type !== 'draft') form.append(description.label);
    const submit = el('button', title); submit.type = 'submit'; submit.disabled = this.busy; form.append(submit);
    form.addEventListener('submit', event => { event.preventDefault(); const parent = this.selected?.resource.id;
      this.perform(async () => {
        let created;
        if (type === 'domain') created = await this.client.createDomain(key.input.value, name.input.value, description.input.value);
        else if (type === 'chain') created = await this.client.createChain(parent, key.input.value, name.input.value, description.input.value, runtimeId.input.value);
        else { const state = await this.client.createDraft(parent, name.input.value, { ...structuredClone(this.context.sample), id: this.selected.resource.runtimeAssemblyLineId }); created = { resource: state.draft, kind: 'DRAFT' }; }
        this.formValues.delete(formKey);
        await this.browse(type === 'domain' ? 'domains' : type === 'chain' ? 'chains' : 'drafts'); await this.choose({ id: created.resource.id, kind: created.kind });
      });
    }); details.append(form); return details;
  }
  details() {
    const box = el('section', undefined, 'box');
    if (!this.selected) { box.append(el('h3', 'Organiser et partager vos chaînes'), el('p', 'Créez un domaine, puis une chaîne et son premier draft. Les drafts sont privés à leur création.'), el('small', 'Le propriétaire du domaine et les administrateurs applicatifs conservent l’accès aux drafts de ce domaine.')); return box; }
    const { resource, permissions, kind } = this.selected;
    box.append(el('h3', resource.name), el('small', `${kind} · propriétaire : ${resource.owner} · état ${resource.abandoned ? 'abandonné' : resource.archived ? 'archivé' : 'actif'}`), el('code', resource.id));
    if (resource.runtimeAssemblyLineId) box.append(el('code', `Identifiant Gear4J : ${resource.runtimeAssemblyLineId}`));
    if (resource.description) box.append(el('p', resource.description));
    const actions = el('div', undefined, 'bar');
    actions.append(btn('Actualiser', () => this.perform(() => this.choose({ id: resource.id, kind })), this.busy));
    if (kind === 'DOMAIN') actions.append(btn('Voir ses chaînes', () => this.perform(() => this.browse('chains', resource.id)), this.busy));
    if (kind === 'CHAIN') actions.append(btn('Voir ses drafts', () => this.perform(() => this.browse('drafts', resource.id)), this.busy));
    if (permissions.includes('MANAGE_ACCESS')) actions.append(btn('Gérer les accès', () => this.perform(async () => { this.accessState = await this.client.access(resource.id); }), this.busy));
    if (permissions.includes('READ_AUDIT')) actions.append(btn('Consulter l’audit', () => this.perform(async () => { this.auditPage = await this.client.audit(resource.id); }), this.busy));
    if (kind === 'DRAFT') actions.append(btn('Historique des révisions', () => this.perform(async () => { this.history = await this.client.revisions(resource.id); }), this.busy));
    box.append(actions);
    if (kind === 'DOMAIN' && permissions.includes('CREATE_CHAIN')) box.append(this.creation('chain'));
    if (kind === 'CHAIN' && permissions.includes('CREATE_DRAFT')) box.append(this.creation('draft'));
    if (permissions.includes('EDIT')) {
      const edit = el('details'); edit.append(el('summary', 'Modifier les informations')); const name = field('Nom', resource.name); const description = field('Description', resource.description || '', 'textarea'); name.input.maxLength = 160; description.input.maxLength = 2048; const formKey = `metadata:${resource.id}`; this.remember(formKey, { name, description }); edit.append(name.label); if (kind !== 'DRAFT') edit.append(description.label);
      edit.append(btn('Enregistrer les informations', () => this.perform(async () => { this.selected = await this.client.metadata(resource.id, resource.version, name.input.value, description.input.value); this.formValues.delete(formKey); this.message = 'Informations enregistrées. Actualisez le draft avant sa prochaine sauvegarde si nécessaire.'; await this.reload(this.cursor); }), this.busy)); box.append(edit);
    }
    if (permissions.includes('ARCHIVE')) { const archive = el('details'); archive.append(el('summary', kind === 'DRAFT' ? 'Abandonner ce draft' : 'Archiver cette ressource'), el('p', 'La définition et l’historique sont conservés. La ressource passe en lecture seule.'));
      archive.append(btn('Confirmer le passage en lecture seule', () => this.perform(async () => { if (this.editor?.hasUnsavedChanges) { this.message = 'Enregistrez ou rechargez les modifications locales avant cette action.'; return; } this.selected = await this.client.archive(resource.id, resource.version); await this.choose({ id: resource.id, kind }); }), this.busy)); box.append(archive); }
    if (this.accessState) box.append(this.accessPanel());
    if (this.history) box.append(this.historyPanel());
    if (this.auditPage) box.append(this.auditPanel());
    return box;
  }
  accessPanel() {
    const box = el('div', undefined, 'box'); const resource = this.selected.resource; const policy = this.accessState.policy; box.append(el('h3', 'Accès explicites'));
    const inherited = el('label', 'Hériter des permissions du parent'); const checkbox = el('input'); checkbox.type = 'checkbox'; checkbox.checked = policy.inheritAccess; checkbox.disabled = this.selected.kind === 'DOMAIN' || !this.accessState.fullAdministration || this.busy; checkbox.addEventListener('change', () => { policy.inheritAccess = checkbox.checked; }); inherited.append(checkbox); box.append(inherited);
    box.append(el('small', 'Les propriétaires et administrateurs gardent leurs droits. READ implique la découverte ; DISCOVER seul ne permet pas de consulter la définition.'));
    const list = el('div', undefined, 'grants');
    policy.grants.forEach((grant, index) => {
      const row = el('div', undefined, 'grant'); const type = el('select'); type.setAttribute('aria-label', 'Type de bénéficiaire');
      for (const value of ['USER', 'GROUP', 'AUTHENTICATED']) { const option = el('option', value); option.value = value; type.append(option); } type.value = grant.subjectType;
      const subject = field('Identifiant du bénéficiaire', grant.subject); subject.input.maxLength = 255; subject.input.disabled = grant.subjectType === 'AUTHENTICATED';
      type.addEventListener('change', () => { grant.subjectType = type.value; grant.subject = type.value === 'AUTHENTICATED' ? '*' : ''; this.render(); }); subject.input.addEventListener('input', () => { grant.subject = subject.input.value; });
      const protectedGrant = grant.permissions.some(p => !this.accessState.delegablePermissions.includes(p)); type.disabled = protectedGrant; subject.input.disabled ||= protectedGrant;
      row.append(type, subject.label); const checks = el('div', undefined, 'checks');
      for (const permission of this.context.permissions) { const label = el('label', permission); const input = el('input'); input.type = 'checkbox'; input.checked = grant.permissions.includes(permission); input.disabled = !this.accessState.delegablePermissions.includes(permission); input.addEventListener('change', () => { grant.permissions = input.checked ? [...grant.permissions, permission] : grant.permissions.filter(p => p !== permission); }); label.prepend(input); checks.append(label); }
      row.append(checks, btn('Retirer ce bénéficiaire', () => { policy.grants.splice(index, 1); this.render(); }, this.busy || protectedGrant)); list.append(row);
    }); box.append(list);
    box.append(btn('Ajouter un bénéficiaire', () => { policy.grants.push({ subjectType: 'USER', subject: '', permissions: ['READ'] }); this.render(); }, this.busy || policy.grants.length >= 64));
    box.append(btn('Enregistrer les accès', () => this.perform(async () => {
      const version = this.accessState.version; const updated = await this.client.setAccess(resource.id, version, structuredClone(policy)); this.selected = updated; this.accessState = null;
      if (!updated.permissions.includes('READ')) { this.selected = null; this.editor = null; this.versions = null; this.message = 'Accès modifiés. Vous ne pouvez plus consulter cette ressource.'; }
      else { this.accessState = await this.client.access(resource.id).catch(() => null); this.message = 'Accès enregistrés. Les prochaines actions seront réévaluées par le serveur.'; }
      await this.reload(this.cursor);
    }), this.busy)); return box;
  }
  historyPanel() {
    const box = el('div', undefined, 'box'); box.append(el('h3', 'Révisions immuables')); const from = field('Révision de départ', '1'); const to = field('Révision à comparer', String(this.selected.resource.head)); from.input.type = to.input.type = 'number'; from.input.min = to.input.min = '1'; box.append(from.label, to.label, btn('Comparer les révisions', () => this.perform(async () => { this.comparison = await this.client.compare(this.selected.resource.id, Number(from.input.value), Number(to.input.value)); }), this.busy)); if (this.comparison) box.append(comparisonPanel(this.comparison));
    for (const revision of this.history.items) { const row = el('div', undefined, 'audit'); row.append(el('strong', `Révision ${revision.number} · ${revision.kind || "STRUCTURED"}`), el('p', `${revision.author} · ${revision.savedAt}`), el('code', revision.revisionId));
      row.append(btn('Repartir de cette révision', () => this.perform(async () => { if (this.editor?.hasUnsavedChanges) { this.message = 'Enregistrez ou rechargez le draft courant avant de créer une variante.'; return; } const state = await this.client.fork(revision.draftId, revision.number, `Reprise révision ${revision.number}`); await this.choose({ id: state.draft.id, kind: 'DRAFT' }); }), this.busy || !this.canFork)); box.append(row); }
    if (this.history.nextCursor) box.append(btn('Révisions suivantes', () => this.perform(async () => { this.history = await this.client.revisions(this.selected.resource.id, this.history.nextCursor); }), this.busy)); return box;
  }
  auditPanel() {
    const box = el('div', undefined, 'box'); box.append(el('h3', 'Audit du périmètre accessible'));
    for (const event of this.auditPage.items) { const row = el('div', undefined, 'audit'); row.append(el('strong', event.action), el('p', `${event.actor} · ${event.occurredAt}`), el('code', `${event.resourceKind} ${event.resourceId} · état ${event.resourceVersion}`));
      if (event.versionId) row.append(el('code', ` · version ${event.versionId}`));
      if (event.accessAfter) { const details = el('details'); details.append(el('summary', 'Changement des accès'), el('pre', JSON.stringify({ before: event.accessBefore, after: event.accessAfter }, null, 2))); row.append(details); } box.append(row); }
    if (!this.auditPage.items.length) box.append(el('p', 'Aucun événement accessible.'));
    if (this.auditPage.nextCursor) box.append(btn('Événements suivants', () => this.perform(async () => { this.auditPage = await this.client.audit(this.selected.resource.id, this.auditPage.nextCursor); }), this.busy)); return box;
  }
}
if (!customElements.get('gear4j-workspace')) customElements.define('gear4j-workspace', Gear4jWorkspace);
