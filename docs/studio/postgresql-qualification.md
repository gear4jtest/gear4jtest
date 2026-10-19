# PostgreSQL — gate obligatoire de Gear4J Studio

**Statut au 15 octobre 2026 : non qualifié.** La couverture ci-dessous décrit le test et ses assertions ; elle ne constitue pas une preuve d'exécution réussie. Aucun serveur PostgreSQL n'est configuré dans l'environnement de préparation, qui ne dispose ni de Docker ni des outils PostgreSQL. Le test obligatoire échoue sans configuration et n'utilise ni H2 ni un skip de substitution.

## Couverture réelle du test

`RequiredPostgreSqlStudioIT.populatedBaselineRestoreChecksumsContextAndConcurrentRestart` utilise un schéma aléatoire dans une base explicitement jetable et appelle la même fixture que `PostgreSqlStudioStoreIT`.

| Sujet | Assertions exécutées lorsque le service est disponible | Limite |
| --- | --- | --- |
| Migration | PostgreSQL majeur 17, baseline V1, données peuplées et redémarrage | Baseline P6 exacte ; pas d'upgrade universel des prototypes antérieurs |
| Sauvegarde/restauration | `pg_dump` custom, suppression du schéma du test, `pg_restore`, comparaison de toutes les lignes | Restauration dans la même base ; pas un exercice de reprise inter-site |
| Reprise après échec | DDL transactionnel annulé, état FAILED, retry implicite refusé, `prepareRetry` puis APPLIED avec SQL/checksum identiques | Échec injecté déterministe, pas une simulation de panne matérielle |
| Concurrence | Deux migrateurs au redémarrage après initialisation/restauration, délais bornés ; conflits optimistes et rollback de la fixture | Pas de test de charge ni de contention forcée ; première initialisation par un migrateur unique |
| Isolation | Ancrage du store dans un contexte, refus de ré-ancrage vers un autre ; schéma du test indépendant | Pas de modèle multi-tenant nouveau |
| Intégrité | Checksum modifié refusé ; SOURCE XML exacte (CRLF/unicode), structured, ACL/audit, versions, publications, scénarios, runs et simulations conservés | Ne prouve pas les garanties d'autres dialectes |

## Prérequis et exécution

Utiliser un runner Linux Java 17, la version Gradle du wrapper, Node 24.19.0, Chromium Playwright et une base PostgreSQL 17 **jetable**, accessible en TCP. Installer `pg_dump` et `pg_restore` 17 dans PATH. L'utilisateur SQL doit pouvoir créer et supprimer son schéma. Ne jamais utiliser une base métier. Les outils natifs reprennent hôte/port/base/utilisateur/mot de passe, pas des options TLS/proxy JDBC arbitraires.

Définir `GEAR4J_TEST_DATABASE_DISPOSABLE=true`, `GEAR4J_TEST_POSTGRES_URL`, `GEAR4J_TEST_POSTGRES_USER` et `GEAR4J_TEST_POSTGRES_PASSWORD`, sans les committer. Puis :

```sh
./gradlew :gear4jtest-studio-jdbc:studioPostgresQualification
./gradlew studioReleaseReadiness --continue
```

Le test est exclu des suites ordinaires et sélectionné explicitement par le gate. La tâche interdit la réutilisation d'un résultat up-to-date ou du build cache. Un test ignoré, un rapport absent, un échec ou une suite vide empêchent la création d'un manifeste PASS.

## CI et publication

`.github/workflows/studio-release-readiness.yml` fournit PostgreSQL 17, les clients natifs 17, les dépendances verrouillées et Chromium. Il se lance sur PR, push main/master ou manuellement. Il est aussi appelé par le workflow de release : `publish` dépend de `studio-qualification`, en plus de preflight et de la matrice JDBC générale. Le workflow réutilisable local est celui du même commit, et reçoit la version candidate de preflight. Aucun secret de publication ne lui est transmis. Un échec, skip ou annulation bloque le job publish.

Les preuves sont archivées dans `studio-readiness-evidence` : JUnit, résultats navigateur, audit npm et `build/reports/studio-release/qualification.json` (version, SHA de commit et hashes des rapports). Le manifeste global de release lie également ce gate à la version/au commit et au même workflow run. Une branche protégée exige en plus de configurer le check dans les règles GitHub ; le YAML seul ne configure pas ces règles.

## Ce qui reste précisément à exécuter

1. Intégrer ces sources sur la branche candidate/PR du dépôt voulu, sans tag de release. Au contrôle du 15 octobre, le workflow est absent des trois branches accessibles de `gear4jtest/gear4jtest` (`master`, `feature`, `feature-parameters`). Aucun dispatch ni écriture distante n'a été effectué.
2. Laisser tourner le workflow de PR, ou, après présence du workflow sur la branche par défaut, utiliser `gh workflow run studio-release-readiness.yml --ref <branche-candidate>`. Ne pas déclencher `release.yml` pour cette seule qualification.
3. Conserver l'URL du run, son SHA exact et l'artefact du même run. Exiger un JUnit avec **1 test, 0 échec, 0 erreur, 0 skip**, et le manifeste PASS. Examiner les résultats de restauration, reprise et isolation couverts par ce test.
4. Corriger tout échec observé, relancer sur le nouveau commit et rendre le check obligatoire dans la protection des branches. Aucun PASS de H2, ancien build ou test synthétique de validateur ne remplace cette exécution.

L'accès disponible ici permet la lecture du dépôt et de ses branches ; il ne fournit pas de mécanisme de dispatch utilisable pour ce workflow absent. La qualification demeure bloquante jusqu'à l'exécution réelle réussie.
