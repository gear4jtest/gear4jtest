# Vérification P2 — 3 octobre 2026

## Périmètre et environnement

Base : archive P1 validée par l’utilisateur. Les changements concernent les
modules Studio, le package frontend extrait et leur documentation. Les sources
historiques du moteur, des générateurs, des adaptateurs JDBC et les seuils de
qualité restent inchangés.

Vérification avec Temurin JDK 17.0.20.1+1 complet, Gradle 9.6.1 du wrapper,
Node 24.19.0 et npm 11.9.0. L’agent Mockito est chargé au démarrage via le
[script de vérification existant](verification-support/mockito-startup.init.gradle),
car l’attachement dynamique n’est pas disponible dans cet environnement.

## Résultats Studio

Les six `check` Studio passent : compilation, Spotless, Checkstyle et tests.
Les six contrôles de couverture Studio passent également, sans modification
des seuils hérités de P1.

| Module | Tests Java réussis |
| --- | ---: |
| `gear4jtest-studio` | 14 |
| `gear4jtest-studio-runtime` | 16 |
| `gear4jtest-studio-demo` | 6 |
| `gear4jtest-studio-spring-demo` | 2 |
| `gear4jtest-studio-json` | 2 |
| `gear4jtest-studio-jdbc` | 2 |
| **Total** | **42** |

Le test d’intégration PostgreSQL supplémentaire compile mais est ignoré faute
de Docker. Les tests de stockage H2 sont réellement exécutés.

**17 tests frontend réussis** : clients HTTP, workspace et éditeur dans jsdom.
Le contrôle `pack:check` fabrique un paquet npm, l’extrait dans un répertoire
isolé et importe son client public, sans dépendance au module demo. Le paquet
`distributions/gear4j-studio-web-0.2.0.tgz` est fourni ; aucune publication sur
un registre n’a été effectuée.

Les nouveaux comportements vérifiés comprennent :

- délégation limitée, protection de l’héritage et des droits administratifs ;
- identité runtime unique et immuable, refus d’un autre contexte persistant ;
- pages ACL bornées, continuation après une page vide, curseurs liés à l’acteur
  et au périmètre, refus des curseurs altérés ;
- résolution des génériques hérités, tableaux et contrats structurels,
  compatibilité à trois états ;
- compilation et test réels de `String → Integer → String`, diagnostic localisé,
  refus contrôlé des contrats externes non pris en charge par le codec de test ;
- prévisualisation XML sans révision implicite, comparaison sémantique et
  comparaison de révisions, y compris identités de nœuds ambiguës ;
- conservation d’une saisie XML après erreur, invalidation d’une ancienne
  prévisualisation et rejet de diagnostics asynchrones devenus obsolètes.

## Contrôle global

Les rapports JUnit des modules recensent **1 454 tests : 1 449 réussis,
5 ignorés, aucun échec ni erreur**. Ces nombres n’incluent pas les tests Node.

Le `check` global reste en échec sur un seul contrôle :
`jacocoCriticalCoverageVerification` exige 0,5 de couverture des branches pour
`io.github.gear4jtest.jdbc.migration.MigrationLockStore`, contre 0,4 mesuré ici.
Ce même blocage est documenté dans la [vérification P1](verification-p1.md),
dans cet environnement sans Docker. La classe et son seuil ne sont pas modifiés.
Le mode `--continue` a permis de vérifier les autres contrôles malgré ce blocage.

Les cinq intégrations ignorées sont `JdbcSchemaMigratorFatalRollbackIT`,
`SimpleChainBuilderDataSourceIT`, `DatabaseAssemblyRunRepositoryMultiDialectIT`,
`ExternalJdbcMultiDialectIT` et `PostgreSqlStudioStoreIT`.

Les contrôles de conventions, dépendances, documentation, identifiants ADR,
budget de suppressions et configuration Java 17 ne signalent pas d’échec.
Un nettoyage des sorties des six modules Studio a précédé la vérification
globale pour éviter une incohérence de cache Spotless déjà observée en P1.

## Reproduire

```sh
./gradlew spotlessApply
./gradlew :gear4jtest-studio:check :gear4jtest-studio-runtime:check \
  :gear4jtest-studio-json:check :gear4jtest-studio-jdbc:check \
  :gear4jtest-studio-demo:check :gear4jtest-studio-spring-demo:check
./gradlew check --continue
```

Si l’attachement dynamique Mockito n’est pas disponible, ajouter à la commande
Gradle `--init-script docs/studio/verification-support/mockito-startup.init.gradle`.
La vérification locale a aussi limité Gradle à deux workers.

Les contrôles de couverture Studio sont indépendants du seuil historique :

```sh
./gradlew jacocoModuleCoverageGear4jtestStudio \
  jacocoModuleCoverageGear4jtestStudioRuntime \
  jacocoModuleCoverageGear4jtestStudioDemo \
  jacocoModuleCoverageGear4jtestStudioSpringDemo \
  jacocoModuleCoverageGear4jtestStudioJson \
  jacocoModuleCoverageGear4jtestStudioJdbc
```

Les tests et le contrôle du paquet frontend font partie du `check` de la démo.
Ils se lancent également séparément :

```sh
npm --prefix gear4jtest-studio-web ci --ignore-scripts
npm --prefix gear4jtest-studio-web test
npm --prefix gear4jtest-studio-web run pack:check
```

## Contrôles non exécutés et suites nécessaires

**PostgreSQL** : Docker n’est pas disponible. Avec Docker, exécuter
`./gradlew :gear4jtest-studio-jdbc:integrationTest` ; H2 ne remplace pas cette
qualification du dialecte PostgreSQL.

**Navigateur** : l’installation de Chromium a échoué, les archives reçues étant
invalides ou tronquées. Aucun résultat Playwright ni contrôle visuel n’est
revendiqué. Les tests jsdom vérifient les interactions, pas le rendu ni
l’accessibilité. Le scénario navigateur existant utilise désormais le package
frontend extrait. Après installation de Chromium, avec les deux démos démarrées
selon l’en-tête du script et uniquement des données synthétiques :

```sh
cd gear4jtest-studio-web
npx playwright install chromium
node ../gear4jtest-studio-demo/src/test/browser/flow.cjs
```

Les mesures de charge ACL, les codecs de données métier, les autres conteneurs
Gear4J et la migration explicite de bases P1 existantes restent les chantiers
identifiés dans le [guide P2](p2.md). La livraison est un incrément expérimental
vérifié dans ce périmètre, pas une qualification de déploiement en production.
