# PostgreSQL — gate obligatoire de Gear4J Studio

**État de qualification : voir le [bilan de clôture](verification-release-closure.md).** Le mainteneur a exécuté le gate avec succès sur PostgreSQL 17 après correction du contexte de la fixture et utilisation des clients natifs 17. Cette exécution locale déclarée reste distincte des preuves CI du commit candidat. Le test obligatoire échoue sans configuration et n'utilise ni H2 ni un skip de substitution.

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

Utiliser un runner Linux Java 17, la version Gradle du wrapper, Node 24.19.0, Chromium Playwright et une base PostgreSQL 17 **jetable**, accessible en TCP. Installer `pg_dump` et `pg_restore` 17 dans PATH. Le test contrôle leurs versions avant de créer son schéma. Le workflow consigne aussi les versions des deux clients dans ses logs. Il partage une identité de contexte unique avec la fixture, vérifie le ré-ancrage identique après restauration et refuse une identité différente. L'utilisateur SQL doit pouvoir créer et supprimer son schéma. Ne jamais utiliser une base métier. Les outils natifs reprennent hôte/port/base/utilisateur/mot de passe, pas des options TLS/proxy JDBC arbitraires.

Définir `GEAR4J_TEST_DATABASE_DISPOSABLE=true`, `GEAR4J_TEST_POSTGRES_URL`, `GEAR4J_TEST_POSTGRES_USER` et `GEAR4J_TEST_POSTGRES_PASSWORD`, sans les committer. Puis :

```sh
./gradlew :gear4jtest-studio-jdbc:studioPostgresQualification
./gradlew studioReleaseReadiness --continue
```

Le test est exclu des suites ordinaires et sélectionné explicitement par le gate. La tâche interdit la réutilisation d'un résultat up-to-date ou du build cache. Un test ignoré, un rapport absent, un échec ou une suite vide empêchent la création d'un manifeste PASS.

## CI et publication

`.github/workflows/studio-release-readiness.yml` fournit PostgreSQL 17, les clients natifs 17, les dépendances verrouillées et Chromium. Il se lance sur PR, push main/master ou manuellement. Il est aussi appelé par le workflow de release : `publish` dépend de `studio-qualification`, en plus de preflight et de la matrice JDBC générale. Le workflow réutilisable local est celui du même commit, et reçoit la version candidate de preflight. Aucun secret de publication ne lui est transmis. Un échec, skip ou annulation bloque le job publish.

Les preuves sont archivées dans `studio-readiness-evidence` : JUnit, résultats navigateur, audit npm et `build/reports/studio-release/qualification.json` (version, SHA de commit et hashes des rapports). Le manifeste global de release lie également ce gate à la version/au commit et au même workflow run. Une branche protégée exige en plus de configurer le check dans les règles GitHub ; le YAML seul ne configure pas ces règles.

## Qualification sans publication

1. Ouvrir la PR candidate pour lancer les workflows, ou déclencher `studio-release-readiness.yml` sur cette branche. Ne pas déclencher `release.yml` pour une qualification seule.
2. Conserver l'URL du run, son SHA et ses artefacts. Exiger **1 test PostgreSQL, 0 échec, 0 erreur, 0 skip** et le manifeste Studio PASS.
3. Exiger également le workflow `Security scan` du même candidat : `verifyReleaseVulnerabilityFeed`, puis `dependencyCheckAggregate`. Configurer `NVD_API_KEY` ou `NVD_DATAFEED_URL` et les credentials du miroir si nécessaire. Sans source autorisée, la qualification échoue ; une PR de fork sans secrets doit être requalifiée sur une branche approuvée, sans utiliser `pull_request_target`.
4. Corriger tout échec et relancer sur le nouveau commit. La publication CI dépend du gate Studio et exige déjà le scan JVM via `releaseCheck` avant JReleaser. Configurer séparément les checks requis dans les règles de branches ; le YAML ne modifie pas ces règles.

Le manifeste Studio et l'audit npm ne remplacent pas le rapport JVM. Le [bilan courant](verification-release-closure.md) distingue les preuves exécutées des qualifications et décisions restantes. Les rapports du 15 octobre restent historiques.
