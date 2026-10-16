# Vérification P1 — 28 septembre 2026

## Périmètre

Base : archive P0 validée par l’utilisateur. P1 ajoute le management durable sans modifier les sources de production historiques du moteur, des générateurs ou des adaptateurs JDBC existants. Les adaptations du build concernent les modules Studio, leurs dépendances de test et leurs contrôles de couverture. Aucun seuil historique n’est abaissé.

Environnement : Temurin JDK 17.0.20.1+1, Gradle 9.6.1 correspondant au wrapper, Node 24.19.0. Le chargement de l’agent Mockito au démarrage est utilisé pour les tests globaux dans cet environnement, via le [script de vérification existant](verification-support/mockito-startup.init.gradle).

## Résultats Studio

- 32 tests Java réussis : 11 management/service, 10 adaptateur runtime, 5 HTTP, 2 Spring, 2 JSON, 2 stockage JDBC/H2.
- 15 tests Node réussis : client public, composant d’édition et workspace, dont droits/capacités, version de concurrence, conservation des saisies après conflit, refus de navigation destructive et formulaire ACL.
- Le test PostgreSQL Testcontainers est compilé mais ignoré faute de Docker. H2 a été effectivement exécuté, notamment sur fichier fermé puis rouvert.
- Les six checks de modules Studio passent, avec Spotless, Checkstyle et tests. Les six contrôles de couverture Studio ainsi que les vérifications des dépendances JDBC, des seuils, des liens de documentation, du budget de suppressions, des identifiants ADR et de la configuration Java 17 passent également.

| Module | Lignes couvertes | Couverture | Minimum |
| --- | ---: | ---: | ---: |
| `gear4jtest-studio` | 503 / 602 | 83,55 % | 50 % |
| `gear4jtest-studio-runtime` | 374 / 430 | 86,98 % | 75 % |
| `gear4jtest-studio-demo` | 258 / 353 | 73,09 % | 65 % |
| `gear4jtest-studio-spring-demo` | 29 / 42 | 69,05 % | 40 % |
| `gear4jtest-studio-json` | 50 / 55 | 90,91 % | 70 % |
| `gear4jtest-studio-jdbc` | 186 / 234 | 79,49 % | 70 % |

Comportements vérifiés : confidentialité des drafts, DISCOVER distinct de READ, partage USER/GROUP, héritage interrompu, révocation, filtrage de l’audit, pages filtrées, deux écritures concurrentes avec un seul commit, annulation complète en cas d’échec de l’audit, révisions immuables, fork exact, archivage des descendants, auteur fourni par l’hôte, refus de falsification JSON, conflit sur ACL/métadonnées/définition, corruption de définition détectée, fermeture/réouverture de la base, contraintes H2 conservées, exécutions Gear4J réelles dans les deux hôtes et round-trip HTTP d’un XML dépassant 16 Ko.

Les tests utilisent des données synthétiques. Les tests H2 et les fixtures de service n’établissent pas la qualification PostgreSQL ni la capacité en charge d’une grande organisation.

## Contrôle global

Les rapports JUnit des modules recensent 1 444 tests : **1 439 réussis, 5 ignorés, aucun échec**. Les tests de conventions Gradle passent également. Le contrôle global ne peut pas être déclaré vert : le seuil historique de `io.github.gear4jtest.jdbc.migration.MigrationLockStore` reste à **0,4 pour un minimum de 0,5** dans cet environnement sans Docker, comme lors de la vérification P0. Le code et le seuil de cette classe restent inchangés.

Les cinq intégrations ignorées sont `JdbcSchemaMigratorFatalRollbackIT`, `SimpleChainBuilderDataSourceIT`, `DatabaseAssemblyRunRepositoryMultiDialectIT`, `ExternalJdbcMultiDialectIT` et le nouveau `PostgreSqlStudioStoreIT`.

La vérification `--continue` a contrôlé les autres gates malgré ce blocage. Une incohérence de cache Spotless (« 0 lint errors » malgré des sources déjà conformes) a nécessité le nettoyage des sorties de build des modules demo/JDBC puis une nouvelle vérification, sans suppression de règle. La politique de dépendances JDBC reconnaît explicitement le nouveau module `studio-jdbc`, le driver H2 du démonstrateur local non publié et H2 en **test uniquement** dans le service neutre et l’hôte Spring. Elle continue de refuser les drivers/Testcontainers dans les autres périmètres. Le socle neutre n’a aucune dépendance de production H2, JDBC ou Spring.

## Reproduire

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-studio:check :gear4jtest-studio-json:check \
  :gear4jtest-studio-jdbc:check :gear4jtest-studio-runtime:check \
  :gear4jtest-studio-demo:check :gear4jtest-studio-spring-demo:check
./gradlew check
```

Les tests frontend sont inclus dans `:gear4jtest-studio-demo:check`, avec `npm ci --ignore-scripts`. Pour les lancer seuls : `npm --prefix gear4jtest-studio-demo test` après installation des dépendances.

Si l’attachement dynamique Mockito n’est pas disponible :

```bash
./gradlew check --continue \
  --init-script docs/studio/verification-support/mockito-startup.init.gradle
```

Les gates Studio peuvent être exécutés indépendamment du gate historique :

```bash
./gradlew jacocoModuleCoverageGear4jtestStudio \
  jacocoModuleCoverageGear4jtestStudioRuntime \
  jacocoModuleCoverageGear4jtestStudioDemo \
  jacocoModuleCoverageGear4jtestStudioSpringDemo \
  jacocoModuleCoverageGear4jtestStudioJson \
  jacocoModuleCoverageGear4jtestStudioJdbc
```

Avec Docker disponible, `./gradlew :gear4jtest-studio-jdbc:integrationTest` exécute le scénario PostgreSQL : migrations répétées, données déclaratives, ACL, audit, pagination, conflit de version et rollback.

## Navigateur

Aucun résultat visuel ou Playwright n’est revendiqué : Chromium n’est pas installé dans cet environnement et sa tentative d’installation a échoué, les archives reçues étant invalides/tronquées. Les 15 tests jsdom vérifient les interactions, pas le rendu visuel.

Le scénario `gear4jtest-studio-demo/src/test/browser/flow.cjs` a été adapté à P1 : création domaine/chaîne/draft, validation, sauvegarde, test, XML, intégration hôte et largeur mobile. Avec les deux applications démarrées, un token synthétique conforme à l’en-tête du script et `GEAR4J_STUDIO_CAPTURE=synthetic` :

```bash
cd gear4jtest-studio-demo
npm ci --ignore-scripts
npx playwright install chromium
node src/test/browser/flow.cjs
```

Ce livrable est un incrément P1 expérimental, pas une qualification de release ou de sécurité d’un déploiement de production. Le [guide P1](p1.md) détaille les contrats, l’authentification applicative, les limites et le démarrage.
