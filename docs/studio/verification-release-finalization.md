# Gear4J Studio — bilan final de préparation à la release

> Historical report (15 October 2026). See [the current release closure](verification-release-closure.md) for current evidence and remaining gates.

**15 octobre 2026. NO-GO pour une publication officielle du SDK expérimental ; NO-GO pour un Studio clé en main en production.** Les corrections et les preuves locales sont livrées. Le blocage prioritaire PostgreSQL demeure une qualification non réalisée, pas un défaut SQL constaté. Aucune publication Maven/npm, aucun tag, aucun push et aucun déploiement officiel n'ont été effectués.

## Corrections livrées

- Le workflow de publication dépend désormais du workflow réutilisable `studio-qualification`, sur le même commit et avec la version candidate. Un échec, skip ou annulation empêche le job de publication. Aucun secret de publication n'est transmis à la qualification.
- Le gate PostgreSQL refuse le cache et les résultats up-to-date. Le manifeste de qualification exige le vrai test réussi sans skip, les cinq parcours navigateur et un audit npm complet. Les preuves sont associées à la version, au commit CI et aux hashes des rapports ; le manifeste global de release référence ce gate et l'artefact du même run.
- L'audit npm réel a trouvé `source-map-js` 1.2.1, dépendance transitive de l'outillage de test, affectée par [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q). Le lockfile utilise désormais 1.2.2 ; le nouvel audit ne signale aucune vulnérabilité. Le gate inclut les dépendances de développement et échoue si le registre ne fournit pas de résultat exploitable.
- Les POM conservent les descriptions expérimentales définies par les modules, grâce à leur évaluation différée. Le contrôle des artefacts vérifie cette mention. Japicmp reconnaît les marqueurs de package importés ou qualifiés et n'essaie plus de résoudre un ancien artefact entièrement expérimental inexistant ; les packages stables restent protégés.
- Le paquet npm contient LICENSE et NOTICE, indique Apache-2.0 et prévoit le dist-tag `experimental`. Le `.tgz` a été régénéré et comparé octet par octet aux sources. Les READMEs, l'installation et les distributions distinguent le SDK expérimental des lanceurs de démonstration.

Aucun Java de production, contrat HTTP, format déclaratif ou octet de migration SQL n'a changé. L'architecture P0–P6 n'est pas rouverte. Java reste en `1.0.0-SNAPSHOT`, npm en `0.6.2`, HTTP en version 2 ; ces versions de travail ne sont pas une décision de première version officielle.

## Résultats exécutés et preuves

| Vérification | Résultat réellement observé | Preuve / portée |
| --- | --- | --- |
| Tests Java des modules | 1 537 PASS, 5 skips Docker, 0 échec/erreur | [Résumé JUnit et inventaire des XML](evidence-release-finalization/junit-summary.json) ; 1 542 cas. |
| Sous-ensemble Studio | 127 PASS, 1 skip Docker | Inclus dans les chiffres Java ; 128 cas. |
| Conventions Gradle / TestKit | 3 PASS | Inclut évaluation tardive des POM et marqueurs Experimental importés/qualifiés, PublicApi maintenu. |
| Tests des scripts de release | 15 PASS | [Sortie Python](evidence-release-finalization/python-tests.txt), dont refus de preuves PostgreSQL absentes, ignorées ou échouées et liaison du gate à la release. |
| Validation des workflows | PASS | [actionlint 1.7.12](evidence-release-finalization/actionlint.txt), contrôles Gradle de sécurité et de contrat ; shellcheck/pyflakes ne sont pas exécutés par actionlint. |
| Tests frontend | 42 PASS | [Sortie des tests](evidence-release-finalization/frontend-tests.txt). |
| Audit npm actualisé | 0 vulnérabilité | Rapports [avant](evidence-release-finalization/npm-audit-before.json) / [après](evidence-release-finalization/npm-audit-after.json), outillage inclus ; ne couvre pas le graphe JVM. |
| Consommateurs Maven indépendants | Java simple et Spring PASS | [Exécution des consommateurs](evidence-release-finalization/maven-consumers.txt) ; résolution depuis les JAR/POM stagés, sans dépendance sur les projets sources. |
| Artefacts Maven | 51 JAR et 19 POM vérifiés | [Inventaire de vérification](evidence-release-finalization/staged-artifacts.txt) : JAR principal/sources/Javadoc, métadonnées et fichiers légaux. Staging local uniquement. |
| Consommateur npm indépendant | PASS | [Installation/imports publics](evidence-release-finalization/npm-consumer.txt), [hash et correspondance du paquet aux sources](evidence-release-finalization/npm-package.json). |
| Format/style, `check`, couverture, baseline et métadonnées | PASS dans les contrôles locaux disponibles | [Log des contrôles locaux](evidence-release-finalization/local-gates.txt). Aucune réduction des seuils de couverture ou de style. |
| Restauration H2 et HTTPS/mTLS | PASS, réexécutés dans les tests Java | `StudioBackupRestoreTest` et `DeploymentSecurityTest`, XML dans l'inventaire JUnit. H2 ne qualifie pas PostgreSQL. |
| Navigateur Chromium réel | 5/5 PASS **le 13 octobre** | [Preuve antérieure conservée](evidence-release-readiness/browser-result.json). Pas de nouvelle exécution navigateur le 15 ; aucun code frontend de production n'a changé. Ce résultat n'est pas présenté comme un nouveau run CI. |
| PostgreSQL obligatoire | **FAIL, non qualifié : 1 test, 1 échec, 0 skip** | Configuration de service absente, échec avant la première requête SQL. [Refus du manifeste](evidence-release-finalization/failed-postgres-rejected.txt) et XML du test dans l'inventaire. |
| Gate de source de vulnérabilités Java | **FAIL, non qualifié** | Absence de `NVD_API_KEY` et `NVD_DATAFEED_URL`. Aucun scan NVD réussi n'est revendiqué. [Échecs initiaux](evidence-release-finalization/initial-gate-failures.txt). |

Le premier lancement agrégé a également échoué sur les métadonnées JReleaser : l'archive n'était pas un checkout Git utilisable. Le contrôle a ensuite réussi dans un dépôt Git local temporaire avec un `origin` correspondant au projet. Il n'y a eu ni push, ni tag, ni invocation de `jreleaserDeploy`, même en dry-run. Cela vérifie le modèle JReleaser, pas la signature ou l'accès à Central.

Les tests utilisent Java 17.0.20.1, Gradle 9.6.1 (checksum du wrapper), Node 24.19.0 et npm 11.9.0. L'agent Mockito est chargé au démarrage via le script de support existant, l'attachement dynamique étant indisponible. Les particularités de proxy/CA du poste restent dans le runner temporaire ; aucune validation TLS n'est désactivée dans le projet. Voir [environnement](evidence-release-finalization/environment.json).

## PostgreSQL : portée et suite exacte

La revue de `RequiredPostgreSqlStudioIT` confirme la couverture de la migration V1, des données peuplées, de `pg_dump`/`pg_restore` avec comparaison de toutes les lignes, de l'échec DDL transactionnel suivi d'une reprise explicite, du checksum altéré, de deux migrateurs au redémarrage et du refus de ré-ancrer un store vers un autre contexte. Les limites sont documentées : première initialisation par un migrateur unique, pas de charge ni de contention forcée, restauration dans la même base et baseline P6 exacte.

**Ces assertions n'ont toujours pas été exécutées avec succès sur PostgreSQL.** Ni Docker ni serveur/outils PostgreSQL ne sont disponibles ici. La lecture des trois branches accessibles du dépôt `gear4jtest/gear4jtest` retourne 404 pour le workflow Studio ; [état distant vérifié](evidence-release-finalization/remote-state.json). Aucun workflow de ce candidat n'a été déclenché.

Le mainteneur doit intégrer ce candidat dans le dépôt voulu, laisser exécuter le workflow de PR (ou le déclencher après son intégration sur la branche par défaut), puis conserver l'URL du run et l'artefact du même SHA. Le résultat exigé est **1 test PostgreSQL, 0 échec, 0 erreur, 0 skip**, et un manifeste de qualification PASS. La procédure, les prérequis et les commandes exactes sont dans [postgresql-qualification.md](postgresql-qualification.md). Le check doit être rendu obligatoire dans les règles de branches ; le YAML livré garantit déjà sa dépendance dans le chemin de publication CI, une fois intégré.

## Blocages restants

| Nature | Ce qui manque | Conséquence |
| --- | --- | --- |
| Qualification prioritaire | PostgreSQL 17 réel et workflow candidat exécutés avec succès | Bloque la publication SDK ; toute anomalie observée devra être corrigée puis requalifiée. |
| Qualification sécurité des dépendances | Scan JVM actualisé avec source NVD autorisée, examen des éventuelles findings ; gates généraux de release dont matrice JDBC | Le rapport npm propre ne suffit pas ; `releaseCheck` complet n'est pas certifié par les contrôles locaux. |
| Décision / qualification de distribution | Version officielle, namespace, droits Maven/npm, signatures et chaîne de publication | Le namespace actuel `io.github.gear4jtest` est un namespace de travail. Une adoption de `io.github.gear4j` exige une modification coordonnée et une nouvelle qualification. Aucun upload n'a été tenté. |
| Qualification du déploiement de production | IdP réel, mapping d'identité, rôles/ACL, isolation des targets, coffre et renouvellement des credentials, TLS entrant/mTLS, proxy et configuration HTTP | Bloque un backoffice clé en main destiné à la production, indépendamment de l'aptitude des composants à être intégrés. |

Les défauts techniques identifiés dans cette finalisation ont été corrigés et les contrôles locaux associés passent. Il serait incorrect d'en déduire l'absence de défauts dans les qualifications qui n'ont pas pu être exécutées.

## Décision par produit

| Produit | Verdict aujourd'hui | Conditions de réexamen |
| --- | --- | --- |
| SDK et composants intégrables, première release expérimentale | **NO-GO pour publication officielle ; candidat prêt à intégrer et qualifier en CI** | PASS PostgreSQL sur le commit candidat, SCA/gates généraux et conditions de registre/signature/namespace validés. L'intégrateur reste responsable du profil d'authentification et de sécurité de son application. |
| Studio clé en main en production | **NO-GO** | Conditions SDK levées, puis hôte de production et matrice de sécurité qualifiés dans l'environnement retenu. Les démos Java/Spring et le lanceur standalone ne livrent pas cette intégration. |

Les limites acceptables d'une première release expérimentale sont l'instabilité annoncée des APIs, l'épinglage des versions, le périmètre de migration explicite, Chromium comme seule matrice navigateur, SSE best-effort et la simulation déclarative sans effets métier. L'absence de preuve PostgreSQL ou de scan de sécurité n'est pas classée comme une limite acceptable. Voir [release-maturity.md](release-maturity.md) et [deployment-security.md](deployment-security.md).

## Reproduction sans publication

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew spotlessApply
./gradlew check releaseMetadataCheck verifyStagedReleaseArtifacts \
  studioConsumerSmoke studioDependencyAudit studioReleaseBaseline coverageReport
# Avec la base PostgreSQL 17 jetable et Chromium configurés :
./gradlew studioReleaseReadiness --continue
```

La dernière commande doit réussir réellement avant décision de publication ; elle ne réussit pas dans l'environnement présent. `releaseMetadataCheck` nécessite les métadonnées d'un checkout Git, contrairement au simple staging local. Aucun résultat synthétique des tests du validateur n'est enregistré comme preuve de qualification réelle.

Message de commit proposé :

```text
chore(studio): enforce release qualification and clarify experimental distribution
```
