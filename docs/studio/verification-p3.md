# Vérification P3 — 3–4 octobre 2026

Base : livraison P2, avec le correctif SOURCE décrit dans son addendum.
Les modifications concernent les modules Studio et leur documentation ; le
moteur Gear4J historique et ses seuils de qualité ne sont pas modifiés.

Environnement : JDK Temurin 17.0.20.1+1, wrapper Gradle 9.6.1, Node 24.19.0,
npm 11.9.0. L’agent Mockito est chargé au démarrage via le
[script existant](verification-support/mockito-startup.init.gradle) lorsque
l’attachement dynamique est indisponible.

## Vérifications ciblées

Le dernier contrôle global du 4 octobre recense **51 tests Java Studio réussis**,
aucun échec et une intégration PostgreSQL ignorée faute de Docker :

| Module | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 15 | 0 |
| studio-runtime | 17 | 0 |
| studio-jdbc | 2 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 13 | 0 |
| studio-spring-demo | 2 | 0 |

Les six contrôles `jacocoModuleCoverageGear4jtestStudio*` passent. Le frontend
compte **21 tests réussis** dans jsdom. Le contrôle `pack:check` crée, extrait et
importe un paquet public autonome. Le livrable npm est
`distributions/gear4j-studio-web-0.3.0.tgz`. Aucune publication dans un registre
n’est effectuée.

Les scénarios P3 vérifient la publication TEST, la promotion RUN réelle avec
AssemblyLineManager et les repositories JDBC, le chargement de latest et son
invalidation, la réouverture de la base et du manager, les versions comparées
sans régénération de leur XML, les tests d’un ancien artefact après modification
du draft, le refus des SOURCE/versions invalides, l’ACL de chaîne distincte de
celle du draft, l’autorisation RUN distincte de TEST et les identités HTTP non
forgeables. Un artefact différent publié hors Studio ne peut pas être écrasé ni
promu sous l’identité Studio attendue. Une écriture réussie suivie d’une réponse
perdue et d’une indisponibilité de lecture reste UNKNOWN puis se réconcilie sans
répéter la publication. Une intention interrompue REQUESTED peut être reprise
explicitement. Les contrôles de stockage vérifient les textes exacts et le refus
d’une empreinte corrompue.

## Reproduire

```sh
./gradlew spotlessApply
./gradlew check --continue
npm --prefix gear4jtest-studio-web test
npm --prefix gear4jtest-studio-web run pack:check
```

Ajouter `--init-script docs/studio/verification-support/mockito-startup.init.gradle`
à Gradle si nécessaire. Les tests frontend et `pack:check` font aussi partie du
`check` de la démo. Les contrôles de couverture ne sont pas désactivés.

## Limites de l’environnement

Docker n’est pas disponible : l’intégration PostgreSQL est compilée mais ne peut
pas être exécutée ici. Elle couvre désormais aussi SOURCE, versions et intentions
de publication. Le test H2 sur fichier ne remplace pas la validation PostgreSQL.

Les vérifications UI sont exécutées avec jsdom. Aucun passage Playwright ni
contrôle visuel navigateur n’est revendiqué ; l’installation de Chromium avait
échoué sur une archive tronquée lors de P2. Les tests contrôlés sont des exécutions
locales réelles sur données synthétiques, pas une preuve d’isolation.

## Résultat du contrôle global

Les rapports JUnit des modules recensent **1 463 tests : 1 458 réussis,
5 ignorés, aucun échec ni erreur**. Les trois tests de conventions de
`build-logic` passent également, en dehors de ce total. Les cinq tests ignorés
sont les intégrations nécessitant Docker : `JdbcSchemaMigratorFatalRollbackIT`,
`SimpleChainBuilderDataSourceIT`, `DatabaseAssemblyRunRepositoryMultiDialectIT`,
`ExternalJdbcMultiDialectIT` et `PostgreSqlStudioStoreIT`.

`spotlessApply` puis `spotlessCheck --rerun-tasks --no-build-cache` passent sur
l’ensemble du projet. Checkstyle, les contrôles d’architecture/documentation,
les budgets de suppressions et les contrôles de couverture par module passent
lors du `check --continue`, exécuté avec l’agent Mockito indiqué ci-dessus et
les six tâches de couverture Studio explicitement demandées.

**Le contrôle global n’est pas vert.** Son unique tâche en échec est
`jacocoCriticalCoverageVerification` : la couverture de branches de
`io.github.gear4jtest.jdbc.migration.MigrationLockStore` est de **0,4 pour un
minimum de 0,5**. Ce même blocage est documenté dans les vérifications P0, P1
et [P2](verification-p2.md) de cet environnement sans Docker. Le code de cette
classe et ses seuils restent inchangés ; aucun contrôle n’a été désactivé ou
affaibli pour la livraison P3.
