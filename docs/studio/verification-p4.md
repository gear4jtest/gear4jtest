# Vérification P4 — 4 octobre 2026

Base : livraison P3 finale, SHA-256
`d090636cf7c7efbd8bfe828d8ddd1203ba18b8c390b06d2f597fc507cc8e4462`.
Les modifications concernent les modules Studio, les composants publics et leur
documentation. Le moteur, les adaptateurs JDBC historiques et les seuils de
qualité ne sont pas modifiés.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1, Node 24.19.0, npm 11.9.0.
Mockito est chargé au démarrage via le [script existant](verification-support/mockito-startup.init.gradle)
lorsque l’attachement dynamique est indisponible.

## Tests fonctionnels

| Module | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 18 | 0 |
| studio-runtime | 17 | 0 |
| studio-jdbc | 4 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 22 | 0 |
| studio-spring-demo | 2 | 0 |

Total : **65 tests Java Studio réussis**, PostgreSQL ignoré faute de Docker.
Le frontend compte **27 tests jsdom réussis**. Le contrôle de paquet crée,
extrait et consomme le paquet ESM sans dépendance à la démo.

Les nouvelles vérifications portent sur :

- Plus de 128 tests successifs, TTL/LRU terminal, replay/conflict et maintien d’un
  RUNNING lors d’une saturation. Une erreur de stockage après exécution produit
  OUTCOME_UNKNOWN et son replay ne réexécute pas.
- Course entre deux services partageant la même base : ancienne complétion après
  nouveau retry, conservation de l’attemptId, de l’auteur et de l’audit courant.
- Une même capacité avec classes et getters TEST/RUN différents, signatures
  incompatibles refusées, compilation/exécution dans les deux modes, publication
  TEST puis promotion RUN de l’artefact logique inchangé.
- Réouverture d’un journal H2 sur fichier, captures masquées et absence du texte
  sensible en SQL ; replay de reçu sans création d’un second run.
- Droits READ_RUNS / READ_OBSERVABILITY distincts, drafts privés exclus des
  résultats de chaîne et des métriques, révocation des droits courants.
- Snapshots d’un vrai test bloqué puis terminé, raccordement d’une chaîne Java
  hôte, refus d’un changement d’identité/hash et gel d’un résultat terminal.
- Scan de 500 observations privées, page vide avec suite, curseur lié au scope,
  aux filtres et à l’acteur, suite après suppression de la ligne frontière.
- Statuts, durées, p95, timeline, inclusion d’étapes rapides mais fréquemment en
  échec, indicateurs partiels en cas de capture tronquée.
- Filtres SQL, conservation des runs actifs lors de la purge, rollback,
  garde-fou de contexte et rejet d’un snapshot stocké corrompu.
- Routes HTTP de lecture, limites/paramètres inconnus, absence de payload sans
  droit d’observabilité, SSE terminal et fermeture après révocation ACL en cours
  de flux. Même parcours de lecture avec le wiring Spring.
- Frontend : rendu texte sûr, pagination/filtres stables, réponses obsolètes
  ignorées, effacement après refus d’accès, snapshots SSE découpés, relecture
  après fermeture, repli en polling et arrêt à la déconnexion du composant.

Le test PostgreSQL a été étendu au journal : insert/update/read, filtres exacts
et purge terminale. Il est compilé, mais n’est pas qualifié par une exécution ici.

## Reproduire

```sh
./gradlew spotlessApply
./gradlew --init-script docs/studio/verification-support/mockito-startup.init.gradle check --continue
npm --prefix gear4jtest-studio-web test
npm --prefix gear4jtest-studio-web run pack:check
```

Le script Mockito est facultatif sur une machine autorisant l’attachement
dynamique. Lancer les intégrations avec Docker pour compléter PostgreSQL et la
matrice de bases du projet. Aucun seuil ni test obligatoire n’est désactivé.

## Limites de qualification

Docker est indisponible. Chromium n’est pas installé dans cet environnement :
les tests UI sont ceux de jsdom, aucun passage Playwright ni contrôle visuel
navigateur n’est revendiqué. Les tests contrôlés sont de vraies exécutions
locales sur fixtures synthétiques ; ils ne prouvent pas un isolement P6.

Le dernier `check --continue` recense **1 477 tests de modules : 1 472 réussis,
5 ignorés, aucun échec ni erreur**. Les trois tests de conventions de build-logic
passent également, hors de ce total. Les cinq intégrations ignorées nécessitent
Docker : JdbcSchemaMigratorFatalRollbackIT, SimpleChainBuilderDataSourceIT,
DatabaseAssemblyRunRepositoryMultiDialectIT, ExternalJdbcMultiDialectIT et
PostgreSqlStudioStoreIT.

Spotless, Checkstyle, les six contrôles de couverture Studio, les contrôles
d’architecture/documentation, le budget de suppressions, les 27 tests frontend
et `pack:check` passent. Les tests JDBC ajoutés qualifient les nouvelles requêtes
et font passer le seuil du module sans modifier la politique de couverture.
Le paquet livré est `distributions/gear4j-studio-web-0.4.0.tgz` ; aucune publication
Maven/npm ni aucun déploiement externe n’a été effectué.

**Le contrôle global n’est pas vert.** Son unique tâche en échec est
`jacocoCriticalCoverageVerification` : `MigrationLockStore`, branches 0,4 pour
minimum 0,5. Ce blocage est déjà documenté dans P3 pour cet environnement sans
Docker. Le code de cette classe et son seuil sont inchangés. Aucun contrôle n’a
été désactivé ou affaibli pour livrer P4.
