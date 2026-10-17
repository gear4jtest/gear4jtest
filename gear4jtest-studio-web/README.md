# @gear4j/studio-web — expérimental 0.3.0

Composants publics ESM et Web Components, sans dépendance au module de démonstration.
Le serveur Java et la démo Spring consomment ces mêmes sources via processResources.

```sh
npm ci
npm test
npm run pack:check
npm pack
```

Installer le .tgz produit dans un portail (`npm install /chemin/gear4j-studio-web-0.3.0.tgz`).
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
Consulter ../docs/studio/p3.md pour les contrats, limites et changements d’API.


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
