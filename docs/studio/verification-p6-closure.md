# Vérification de la clôture P6 — 8 octobre 2026

Base utilisateur P6 : SHA-256
`67fb308e0725ed251b903b484eb9beb76531aaab34ad2add4862618caaea3451`.
Les changements concernent les tests de conformité, les diagnostics de provenance,
la permission de lecture des simulations, leur affichage et la documentation.
Aucune modification du moteur métier, des seuils ou du schéma SQL.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1, Node 24.19.0 et npm 11.9.0.
Mockito est chargé au démarrage par le script de vérification existant, faute
d’attachement dynamique disponible dans cet environnement.

## Résultats exécutés

| Module Studio | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 29 | 0 |
| studio-runtime | 43 | 0 |
| studio-http | 18 | 0 |
| studio-jdbc | 7 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 15 | 0 |
| studio-spring-demo | 2 | 0 |
| studio-standalone | 9 | 0 |

**125 tests Java Studio réussis**, aucun échec ni erreur ; **41 tests frontend
jsdom réussis**. Les huit contrôles de couverture Studio passent sans abaissement
de seuil. Le paquet public **0.6.1** est produit, sa consommation locale est
vérifiée par `pack:check` et son contenu correspond aux sources frontend livrées.

Le contrôle global compte **1 537 tests de modules : 1 532 réussis, 5 ignorés,
aucun échec ni erreur**. Les trois tests build-logic passent également, hors
de ce total. Le contrôle de couverture critique historique de
`io.github.gear4jtest.jdbc.migration.MigrationLockStore` reste en échec : ratio de
branches 0,4, minimum 0,5. La classe et le seuil sont inchangés. Le build global
n’est donc pas présenté comme entièrement vert.

Une erreur transitoire de sérialisation interne à Spotless a affecté le premier
contrôle core. La relance de `:gear4jtest-core:spotlessApply` passe, sans modifier
les sources core. La relance globale se termine avec un seul échec : le seuil MigrationLockStore
décrit ci-dessus. Spotless, Checkstyle, liens documentaires et contrôles
architecturaux passent. Aucun contournement
des règles de formatage, de style ou de couverture n’est ajouté.

## Comportements vérifiés

- 19 cas différentiels : même déclaration et entrée, simulation indépendante contre XML/compilation/executor Gear4J réels, avec opérateurs purs ; ordre, deux branches, propagation, sorties et arrêts/échecs concordants.
- Les tests préexistants qui interdisent les interactions avec l’executor et le ResourceFactory du simulateur restent verts.
- Contexte historique identique, build seul changé, catalogue seul changé et les deux changés ; diagnostics WARNING non bloquants sur la version, absents sur le draft.
- Diagnostics identiques dans le plan et le rapport, maintenus sans captures et après réouverture du stockage, sans recalcul selon le runtime de consultation.
- READ_SIMULATIONS sans SIMULATE ni READ_RUNS autorise les rapports uniquement ; READ_RUNS + READ_OBSERVABILITY n’autorise pas les simulations.
- SIMULATE seul autorise la commande et son retry propre, sans ouvrir le GET ni l’historique ; confidentialité des drafts privés et filtrage ACL conservés.
- Rendu frontend des avertissements du plan et d’un rapport relu, sans nouvelle simulation.
- Documentation des limites, distinction DELIVERED / DEFERRED, guides de consommation et ADR mis à jour.

## Commandes

```sh
./gradlew -I docs/studio/verification-support/mockito-startup.init.gradle \
  :gear4jtest-studio-runtime:test --tests '*DeclarativeSimulatorConformanceTest' \
  :gear4jtest-studio-http:test --tests '*StudioSimulationTest'
./gradlew -I docs/studio/verification-support/mockito-startup.init.gradle spotlessApply check --continue
./gradlew :gear4jtest-core:spotlessApply --stacktrace
./gradlew -I docs/studio/verification-support/mockito-startup.init.gradle check --continue
```

Dans `gear4jtest-studio-web` :

```sh
npm ci --ignore-scripts
npm test
npm run pack:check
npm pack --pack-destination ../distributions
```

JDK et paramètres de proxy/truststore sont fournis par l’environnement, sans
modification des scripts du projet. Après reprise de l’environnement, un cache
Gradle API incomplet a été régénéré ; il ne figure pas dans l’archive.

## Limites conservées

Les cinq intégrations nécessitant Docker restent ignorées :
JdbcSchemaMigratorFatalRollbackIT, SimpleChainBuilderDataSourceIT,
DatabaseAssemblyRunRepositoryMultiDialectIT, ExternalJdbcMultiDialectIT et
PostgreSqlStudioStoreIT. Aucun PostgreSQL réellement exécuté ni navigateur réel
n’est revendiqué ici. Aucun test mTLS/OIDC externe ni publication de registre.

Le corpus différentiel protège le sous-ensemble déclaratif actuel ; il ne
qualifie ni les autres constructions Gear4J, ni l’isolation de code Java arbitraire.
La [préparation à la release](release-readiness.md) reste proposée, non démarrée.
Voir [p6-closure.md](p6-closure.md) pour les décisions et les limites ; la
[qualification P6 précédente](verification-p6.md) reste un état historique distinct.
