# Vérification P6 — 7 octobre 2026

Base utilisateur : `gear4jtest-studio-p5(1).zip`, SHA-256
`3f6e4ed022799f76b6c48cffdc00bcafc6fea67ae8162d8508c490bdc239e945`.
Les changements concernent les modules Studio, leurs tests, leur documentation
et le paquet frontend. Le moteur Gear4J, ses seuils de qualité et ses migrations
restent inchangés ; seule la migration expérimentale Studio ajoute les rapports
de simulation.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1 correspondant au wrapper,
Node 24.19.0, npm 11.9.0. Mockito chargé au démarrage par le
[script existant](verification-support/mockito-startup.init.gradle), faute
d’attachement dynamique dans cet environnement.

## Résultats

| Module | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 29 | 0 |
| studio-runtime | 24 | 0 |
| studio-jdbc | 7 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 15 | 0 |
| studio-http | 14 | 0 |
| studio-spring-demo | 2 | 0 |
| studio-standalone | 9 | 0 |

**102 tests Java Studio réussis**, aucun échec ni erreur. Les **40 tests frontend
jsdom** passent. Les huit contrôles de couverture Studio passent, sans abaissement
de seuil ni ajout de suppression. Le paquet public 0.6.0 est produit et son
installation locale contrôlée par `pack:check`.

Le contrôle global compte **1 514 tests de modules : 1 509 réussis, 5 ignorés,
aucun échec ni erreur**. Les trois tests de conventions build-logic passent
également, hors de ce total.

Le `check` global reste en échec uniquement sur le contrôle critique historique
`MigrationLockStore` : couverture de branches 0,4, minimum 0,5. Cette classe et
ce seuil sont inchangés. Ne pas présenter le build global comme entièrement vert.
Les autres contrôles, dont Spotless, Checkstyle, liens documentaires, contrats
architecturaux et couverture Studio, passent.

## Comportements exercés

- Principal canonique stable, séparation d’issuer, encadrement sans collision de concaténation et rejet Unicode invalide.
- Quota par cible pendant un provider de credentials bloqué, accès conservé à une autre cible, libération des permits et propriété de l’executor HTTP injecté.
- Contrat exhaustif des routes, classification fédérée/local SSE et refus de chemins arbitraires.
- Substitution complète sans interaction avec executor ou ResourceFactory, capability interdite en TEST simulable, références métier jamais résolues.
- Choix GEL, deux branches sélectionnables, branche non sélectionnée explicitement mockée, scalaire intermédiaire Integer, texte exact, assertion avant capture.
- Profil incomplet, obsolète, capability modifiée, sortie incompatible, scalaire hors bornes, échec de mock et erreur de condition.
- Captures masquées, non capturées, policy en échec et refus des captures trop grandes.
- Rapports durables après réouverture, idempotence persistée, audit unique, conflit de requestId et absence de faux runs.
- Permission SIMULATE sans TEST, retrait des captures sans READ_OBSERVABILITY, filtrage des rapports de drafts privés et refus de SOURCE.
- Routes P6 via le standalone vers deux vrais hôtes Java et Spring avec bases H2 distinctes ; distribution HTTP du composant de simulation.
- Frontend : mocks exacts, refus d’une simulation non enregistrée, nœuds des deux branches préparés explicitement, sauvegarde du profil, relecture sans nouvelle exécution, rendu sûr des valeurs et effacement sur refus.

## Commandes reproductibles

Après les tests ciblés, le contrôle global exécuté est :

```sh
./gradlew -I docs/studio/verification-support/mockito-startup.init.gradle spotlessApply check --continue
```

Le JDK 17 et les paramètres locaux de proxy/truststore ont été fournis par
l’environnement, sans modification des scripts du dépôt. Le script Mockito
n’est utile que si l’attachement dynamique est indisponible.

Dans `gear4jtest-studio-web` :

```sh
npm ci --ignore-scripts
npm test
npm run pack:check
npm pack --pack-destination ../distributions
```

L’archive des sources inclut le wrapper complet, le .tgz public, les guides et
l’ADR 0054. Elle exclut les caches, builds, node_modules et bases de démonstration.

## Limites de validation

Aucun test réel mTLS, proxy d’entreprise ou serveur OIDC externe n’est revendiqué.
L’injection HTTP et les contrats d’identité sont exercés localement.
Les cinq intégrations nécessitant Docker restent ignorées :
JdbcSchemaMigratorFatalRollbackIT, SimpleChainBuilderDataSourceIT,
DatabaseAssemblyRunRepositoryMultiDialectIT, ExternalJdbcMultiDialectIT et
PostgreSqlStudioStoreIT. PostgreSQL n’est donc pas qualifié par exécution ici.
L’interface est testée avec jsdom, pas par une session visuelle de navigateur.

Le périmètre de simulation est celui du [guide P6](p6.md) : substitutions
complètes de données scalaires et conditions du modèle déclaratif. Il ne
qualifie pas l’exécution isolée d’opérateurs Java arbitraires.
