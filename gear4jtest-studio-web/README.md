# @gear4j/studio-web — expérimental 0.4.1

Composants publics ESM et Web Components, sans dépendance au module de démonstration.
Le serveur Java et la démo Spring consomment ces mêmes sources via processResources.

```sh
npm ci
npm test
npm run pack:check
npm pack
```

Installer le .tgz produit dans un portail (`npm install /chemin/gear4j-studio-web-0.4.1.tgz`).
Importer `ManagementClient` depuis `@gear4j/studio-web/client`, `Gear4jStudio` depuis
`@gear4j/studio-web/editor`, ou `Gear4jWorkspace` depuis `@gear4j/studio-web/workspace`.
L’entrée principale enregistre les Web Components ; l’entrée client fonctionne sans DOM.
Pas de bundler imposé ; un portail peut servir les fichiers ESM de src sous un même répertoire.
Les navigateurs doivent disposer de Web Components, structuredClone et crypto.randomUUID.

```js
import { ManagementClient } from '@gear4j/studio-web/client';
import '@gear4j/studio-web/workspace';
await document.querySelector('gear4j-workspace').configure({
  client: new ManagementClient({ baseUrl: '', tokenProvider: () => tokenDeSession })
});
```

L’hôte fournit l’authentification. Aucun stockage du token, aucune relance automatique de POST.
Pour l’éditeur seul : récupérer le draft avec ManagementClient, construire ScopedAuthoringClient,
puis appeler configure({client: scoped, revision: scoped.flat(state)}).
Les événements studio:revision / studio:run contiennent uniquement les identifiants et statuts.
Consulter ../docs/studio/p41.md pour les contrats, limites et changements d’API.


Le composant de cycle de vie est également consommable séparément :

```js
import '@gear4j/studio-web/versions';
await document.querySelector('gear4j-versions').configure({
  client,
  chainId
});
```

Pour figer depuis un éditeur, fournir `canFreeze` obtenu depuis les permissions
PUBLISH de la chaîne et `getDraft: () => ({ revision: editor.revision,
dirty: editor.hasUnsavedChanges })`. Ces informations ne remplacent jamais les
contrôles serveur. Le composant refuse de figer une SOURCE ou des modifications
locales non enregistrées. Publication TEST et promotion RUN sont distinctes ;
les reprises nécessitent une action explicite après lecture de l’état.
Les scripts sont servis ensemble (dont `studio-versions.mjs`) par les démos.

L’exploitation est également un composant public autonome :

```js
import '@gear4j/studio-web/runs';
await document.querySelector('gear4j-runs').configure({
  client,
  scopeId: chainId, // ou draftId
  canReadRuns: permissions.includes('READ_RUNS'),
  canReadObservability: permissions.includes('READ_OBSERVABILITY')
});
```

Le serveur vérifie toujours les droits courants. La sélection d’un run émet
`studio:run` avec l’identifiant d’observation Studio et son statut ; l’identifiant
Gear4J figure dans son détail. Aucun payload ne voyage dans cet événement DOM.
L’actualisation automatique est opt-in : snapshots SSE avec Authorization en
header, relecture du snapshot après fermeture, polling à défaut. Aucun event log
ni replay durable n’est supposé. Le retrait du composant arrête le suivi ; une
réponse 401/403/404 efface les données affichées. Les requêtes sont exclusivement
des GET : ce composant ne peut pas lancer ou répéter une exécution.


Les scénarios sont également disponibles seuls :

```js
import '@gear4j/studio-web/scenarios';
await document.querySelector('gear4j-scenarios').configure({
  client,
  scopes: [{ id: draftId, label: 'Draft privé', permissions }],
  getTargets: () => [{
    key: editor.revision.revisionId,
    label: `Draft · révision ${editor.revision.number}`,
    target: { draftId, revision: editor.revision.number },
    disabled: editor.hasUnsavedChanges || editor.revision.kind === 'SOURCE'
  }]
});
```

Pour une version figée, fournir `target: { versionId }` avec clé/étiquette
correspondantes. Les scopes et permissions viennent du serveur, qui recontrôle
chaque opération. Un scénario de draft reste limité à ce draft.
`studio:scenario` émet seulement id/révision après sauvegarde ; `studio:run`
émet requestId/état après soumission. `hasUnsavedChanges` protège la navigation.
Le reçu est relu par GET après une réponse perdue, sans répétition du POST.
Les données sont persistées après admission par ScenarioDataPolicy.

Les indicateurs regroupent les stations par nodeId et dimensions de version,
mode, artefact, catalogue et build, affichées dans chaque ligne. staleAt signale
une observation ancienne sans changer le résultat métier.
