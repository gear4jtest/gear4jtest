# Vérification P0 — 27 septembre 2026

## Résultats confirmés

- `./gradlew spotlessApply` : réussi ; aucune modification des sources historiques due au formatage.
- `./gradlew :gear4jtest-studio:check :gear4jtest-studio-runtime:check :gear4jtest-studio-demo:check :gear4jtest-studio-spring-demo:check` : réussi, y compris compilation, Checkstyle, Spotless et tests.
- 17 tests Java : 3 service/révisions/concurrence, 10 adaptateur runtime/Gear4J/XML/sécurité/capture, 2 HTTP, 2 Spring (moteur réel et cycle de vie de l'hôte HTTP). Aucun échec, aucun test ignoré.
- 10 tests Node : 6 client public, 4 composant DOM avec jsdom. Aucun échec.
- Les quatre gates de couverture P0 passent, ainsi que `verifyCoveragePolicy`, `verifySuppressWarningsBudget`, `verifyDocumentationLinks`, `verifyDecisionIdentifiers` et `verifyJava17AndArchiveConfiguration`.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1 (distribution correspondant au checksum du wrapper), Node 24.19.0. Les dépendances ont été réinstallées depuis leurs versions verrouillées pour la passe finale.

| Module | Lignes couvertes | Couverture | Minimum P0 |
| --- | ---: | ---: | ---: |
| `gear4jtest-studio` | 89 / 168 | 52,98 % | 50 % |
| `gear4jtest-studio-runtime` | 374 / 430 | 86,98 % | 75 % |
| `gear4jtest-studio-demo` | 209 / 294 | 71,09 % | 65 % |
| `gear4jtest-studio-spring-demo` | 26 / 38 | 68,42 % | 40 % |

Commandes complémentaires de couverture :

```bash
./gradlew jacocoModuleCoverageGear4jtestStudio \
  jacocoModuleCoverageGear4jtestStudioRuntime \
  jacocoModuleCoverageGear4jtestStudioDemo \
  jacocoModuleCoverageGear4jtestStudioSpringDemo
```

Les thresholds existants sont inchangés. Le gate du nouveau démonstrateur Spring avait initialement révélé un démarrage non exercé ; le test d'hôte et la gestion explicite de son cycle de vie résolvent ce manque sans abaisser son seuil.

Les preuves couvrent des exécutions Gear4J réelles, les deux branches GEL, les valeurs String échappées, le refus des extensions XML inconnues/Java arbitraire/DTD, les permissions distinctes des capabilities, les références de ressources, le masquage et le défaut de capture, la conservation de révisions exactes et l'absence de double exécution pour une même requête. La validation ne résout ni n'exécute les opérateurs.

## Contrôle global

Le contrôle global n'est **pas vert**. Deux essais ont été effectués avant la dernière vérification ciblée de l'hôte Spring :

1. `./gradlew check` : échec de l'initialisation Mockito dans 36 tests du cœur, car l'attachement dynamique de l'agent Byte Buddy est indisponible dans cet environnement.
2. Chargement du même agent Mockito au démarrage des JVM de test, sans modification des dépendances ni des gates : 1 425 tests recensés dans les rapports des modules, dont **1 421 réussis, 4 ignorés, aucun échec**. Le build s'arrête ensuite sur le gate historique de `io.github.gear4jtest.jdbc.migration.MigrationLockStore` : couverture de branches **0,4 pour un minimum de 0,5**.

Les quatre tests ignorés sont les intégrations Docker `SimpleChainBuilderDataSourceIT`, `JdbcSchemaMigratorFatalRollbackIT`, `DatabaseAssemblyRunRepositoryMultiDialectIT` et `ExternalJdbcMultiDialectIT`. Le seuil JDBC est conservé. Aucune modification des sources de production historiques n'a été introduite pour faire passer ce contrôle.

Pour reproduire le second essai lorsque l'attachement dynamique n'est pas disponible :

```bash
./gradlew check --init-script docs/studio/verification-support/mockito-startup.init.gradle
```

Ce [script facultatif](verification-support/mockito-startup.init.gradle) utilise uniquement le JAR Mockito déjà résolu par chaque tâche de test. Il ne masque aucun échec et ne fait pas partie de la configuration normale du produit. Un contrôle global en CI avec les intégrations Docker reste nécessaire avant qualification d'une release.

## Limite de vérification visuelle

Chromium a refusé de démarrer dans cet environnement : `socket() failed: Operation not permitted` lors de l'initialisation du navigateur. Aucun rendu visuel ou résultat Playwright n'est revendiqué. Les tests jsdom vérifient les interactions et l'absence d'interpolation HTML ; ils ne prouvent pas la mise en page dans un navigateur.

Un scénario Playwright reproductible est fourni dans `gear4jtest-studio-demo/src/test/browser/flow.cjs` pour les hôtes Java et Spring, la page intégrée, le test contrôlé et la largeur mobile. Voir le guide P0 pour le lancer localement.

## Portée

Le prototype est local et en mémoire. Authentification de production, ACL durables, audit, publication/promotion, observation des chaînes programmatiques et isolation de simulation restent hors P0. Il ne s'agit pas d'une qualification de release du dépôt complet.
