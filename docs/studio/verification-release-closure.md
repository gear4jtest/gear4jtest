# Gear4J Studio — bilan Release Readiness, 22 octobre 2026

**SDK expérimental : NO-GO pour une publication officielle. Studio clé en main de production : NO-GO.**
Les défauts identifiés sont corrigés et les tests applicatifs passent avec les dépendances corrigées. Des gates obligatoires restent non qualifiés. Aucune publication, signature, création de tag ou modification de déploiement n'a été effectuée.

Ce bilan remplace le [bilan historique du 15 octobre](verification-release-finalization.md). Base : 78f9a4041c285986e5c8ab79071d9e5b054084fa, plus le patch fourni, pas encore commité à distance. APPLICATION.txt contient son empreinte.

## Corrections intégrées

- PostgreSQL : identité RuntimeContext commune aux fixtures ; restauration acceptée pour le même contexte et refusée pour un autre. Contrôle de **pg_dump 17 et pg_restore 17**, avec timeout, avant création du schéma.
- GitHub Actions : healthcheck Docker corrigé (guillemets) ; clients 17 explicitement sélectionnés et versions affichées ; scripts invoqués par bash/python3 sans dépendre du bit exécutable.
- Test de retry du chargement : synchronisation déterministe avec le nettoyage asynchrone, vérification du maintien de la réservation puis de sa libération. Runtime de production inchangé.
- Démo : référence de ressource absente traitée par une erreur explicite, au lieu d'une NullPointerException ; tests de câblage renforcés, seuils de couverture inchangés.
- Dépendances : Jackson **2.21.7**, même série 2.21 ; Commons Compress **1.28.0** contraint uniquement dans les tests d'intégration utilisant Testcontainers, sans ajout au runtime.
- Sécurité CI : scan JVM sur PR/push en plus des déclenchements manuel et planifié ; préflight de la source NVD et paramètres de miroir ; contrats des workflows renforcés. Aucune suppression de vulnérabilité ajoutée.

Les APIs publiques, le schéma SQL et l'architecture P0–P6 ne changent pas. La documentation distingue le SDK expérimental des démonstrations et de l'intégration de production à fournir par l'hôte.

## Vérifications réellement exécutées

| Vérification | Résultat et portée |
| --- | --- |
| Suite complète du 21 octobre | **PASS** : check, coverageReport, releaseMetadataCheck, verifyStagedReleaseArtifacts, studioConsumerSmoke, studioReleaseBaseline ; 408 tâches, 5 min 43 s. Avant les mises à jour de dépendances ; rapports bruts archivés. |
| Requalification du 22 octobre après mises à jour | **PASS** : test, integrationTest, coverageReport, releaseMetadataCheck, verifyStagedReleaseArtifacts, studioConsumerSmoke, studioReleaseBaseline ; 250 tâches, 5 min 50 s. |
| Compatibilité complémentaire | **PASS** : 3 tests build-logic et 2 tests DSL du plugin Gradle après mises à jour. |
| Total JVM après mises à jour | **1 554 cas, 1 549 exécutés, 0 échec/erreur, 5 skips** nécessitant Docker. |
| Couverture et contrats de workflows | **PASS** après mises à jour : coverageVerification, verifyReleaseWorkflowContract, verifyWorkflowSecurity. Démo : lignes 80 % (220/275), branches 72,5 % (87/120). |
| Consommation indépendante | **PASS** Java et Spring depuis les JAR/POM du staging après mises à jour ; métadonnées et 87 artefacts JAR/POM/module vérifiés. Aucun registre distant utilisé pour publier. |
| Frontend et outils | **PASS** : 42 tests frontend dans Gradle ; 27 tests npm séparés (recouvrement, ne pas additionner), consommation indépendante de tous les exports npm, 15 tests Python, actionlint 1.7.12. ShellCheck/Pyflakes désactivés dans cet appel actionlint. |
| Paquet npm | **PASS** : 14 fichiers du TGZ conformes aux sources ; audit npm à 0 vulnérabilité signalée, dépendances de développement incluses. |
| Audit JVM OSV | **PASS** : 105 coordonnées Maven réellement résolues, aucun avis retourné après correction des 9 avis initiaux sur Jackson et Commons Compress. Ne remplace pas Dependency-Check/NVD. |
| HTTPS/mTLS | **PASS** : DeploymentSecurityTest et cas de refus, également après mises à jour. Ne qualifie pas un IdP réel. |
| SQL/protocole | **PASS** : baseline et contrats figés ; sauvegarde/restauration H2 exécutée. H2 ne qualifie pas PostgreSQL. |
| PostgreSQL 17 | **PASS rapporté par le mainteneur**, sur serveur réel et clients 17. Rapports JUnit non fournis ici ; candidat corrigé encore à qualifier en CI. |
| Reproductibilité à version fixe | **PASS**, le 22 octobre de 12:52 à 13:00 UTC : deux constructions à 1.0.0-reproducibility, **87 artefacts JAR/POM/module identiques**. Logs et deux listes SHA-256 conservés. |

Les cinq suites ignorées faute de Docker sont ExternalJdbcMultiDialectIT, JdbcSchemaMigratorFatalRollbackIT, DatabaseAssemblyRunRepositoryMultiDialectIT, SimpleChainBuilderDataSourceIT et PostgreSqlStudioStoreIT. Elles ne sont pas comptées comme qualifiées.

SHA-256 du paquet npm vérifié : ccaa6e1727f0e36bf0f9493e69b68a4c7bda9349144413da9e4c720e136cfaa4.

### Conservation et portée des preuves

Le dossier conserve les rapports JUnit bruts du 21 octobre, les logs ciblés, les audits OSV initial/final, l'inventaire Maven, les contrôles frontend, les constats GitHub et la tentative navigateur. OSV couvre les configurations runtime, tests unitaires et d'intégration du build principal ; pas les plugins/outils de build ni les fichiers locaux sans coordonnées Maven.

Les résultats du 22 octobre après l'audit final ont été lus dans les sorties d'exécution : tests/staging 07:39–07:45 UTC, NVD/couverture 07:45–07:48, compatibilité 07:48–07:49. Leurs rapports bruts ont été perdus lors de la réinitialisation du workspace. Le fichier evidence/transcript-observations-22.json les consigne comme observations des sorties de session, **pas comme rapports JUnit reconstruits**. Les rapports archivés du 21 octobre ne sont pas attribués aux nouvelles dépendances. Une CI du candidat reste nécessaire pour un dossier complet, traçable à un commit.

La vérification de reproductibilité terminée à 13:00 UTC dispose, elle, de ses preuves brutes : evidence/reproducibility-final-21.log, evidence/reproducibility-final-21.json et les deux manifestes de evidence/release-evidence-21/reproducibility-final-22/. Le suffixe 21 du nom de log vient de l'outil de qualification ; ses horodatages sont ceux du 22 octobre. L'essai antérieur à version SNAPSHOT avait comparé des noms Maven horodatés : il ne qualifiait pas la reproductibilité à version fixe et n'a pas motivé de changement du build.

## Gates non qualifiés

### Dependency-Check / NVD

Le premier préflight a refusé l'absence de NVD_API_KEY ou NVD_DATAFEED_URL. Le 22 octobre, une tentative réelle avec le [flux NVD officiel supporté](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html) a passé le préflight, puis **échoué** dans dependencyCheckAggregate.

Des réponses HTTP 403 ont bloqué les métadonnées NVD, RetireJS, les suppressions officielles de Dependency-Check et le flux CISA KEV. Le scanner a déclaré ne pas pouvoir poursuivre l'analyse. La transmission à Gradle du proxy courant et du truststore a été vérifiée et était correcte. Un téléchargement indépendant des métadonnées NVD avait réussi ; cela ne constitue pas un scan.

Aucun rapport NVD réussi n'est disponible. Aucun analyseur, seuil CVSS, contrôle d'erreur ou source obligatoire n'a été désactivé. Fournir en CI une clé NVD ou un miroir autorisé et l'accès aux autres sources du scanner, puis obtenir une analyse complète. **OSV à zéro ne lève pas ce gate.**

### Qualité complète et navigateur

Le check complet a passé le 21 octobre. Le nouvel appel Spotless du 22 octobre est bloqué avant formatage par la page « Temporary shutdown » du dépôt officiel Eclipse 4.35, également constatée sur son archive. Aucun formateur ni seuil n'a été changé. Rejouer check une fois sa dépendance disponible ; tests et couverture du candidat corrigé ont passé séparément.

Chrome for Testing 151.0.7922.34 a réellement été lancé, mais a échoué sur « socket() failed: Operation not permitted », avant toute page. **Aucun des cinq parcours navigateur n'a été exécuté lors de cette tentative.** Limitation de cet environnement, pas échec fonctionnel observé. Rejouer sur le runner CI avec le navigateur Playwright verrouillé.

### GitHub Actions et PostgreSQL

La lecture GitHub du 22 octobre confirme master sur la base indiquée, sans exécution qualifiant le patch local.

| Run observé | Conclusion |
| --- | --- |
| [Studio readiness 39274843826](https://github.com/gear4jtest/gear4jtest/actions/runs/39274843826) | Échec du démarrage Docker avant toute assertion PostgreSQL ; guillemets corrigés. |
| [CI 39274843750](https://github.com/gear4jtest/gear4jtest/actions/runs/39274843750) | Permissions des scripts et race du test de retry ; corrections intégrées. |
| [Security 39227650443](https://github.com/gear4jtest/gear4jtest/actions/runs/39227650443) | Annulé après une heure, aucun scan NVD réussi. |

La création du candidat via l'intégration GitHub a été refusée : **HTTP 403 « Resource not accessible by integration »**. Aucun commit, branche ou PR distant créé ; les workflows corrigés n'ont pas pu être déclenchés ici. L'ancien échec Docker n'est pas un échec des assertions PostgreSQL ; le PASS local du mainteneur n'est pas un run CI.

Le gate utilise PostgreSQL 17 et les clients natifs 17 sur une base jetable autorisée. Couverture : migration/checksum, sauvegarde native puis restauration avec comparaison des données (XML SOURCE, Unicode, CRLF inclus), rollback d'une migration en échec, retry explicite, migrations concurrentes et redémarrage, même contexte accepté et autre contexte refusé. Limites : restauration dans la même base, pas de charge ni de preuve de toutes les contentions, migration depuis la baseline P6 exacte. Voir [qualification PostgreSQL](postgresql-qualification.md).

La publication impose toujours publish.needs = [preflight, database-matrix, studio-qualification]. releaseCheck impose aussi le scan JVM avant JReleaser. **Aucun gate assoupli.** La branche était non protégée selon GitHub ; les required checks au merge sont distincts des dépendances YAML de publication.

Avant publication, intégrer le patch puis exécuter **sur le même commit candidat** CI, Studio Release Readiness et Security. Conserver SHA et rapports ; contrôler notamment le JUnit PostgreSQL obligatoire sans skip et le manifeste Studio PASS. Ne pas déclencher le workflow de publication pour cette qualification.

## Sécurité et distribution : portée réelle

Les tests existants couvrent les refus d'authentification/autorisation, les attentes d'identité/contexte des targets, les routes autorisées, les credentials et HTTPS/mTLS avec erreurs de confiance/hostname. Ils ne qualifient pas une topologie réelle.

Le backoffice de production exige encore un IdP et des rôles réels, des credentials par target, les certificats/truststores et le proxy de l'organisation, la politique de secrets et la configuration HTTP vérifiés dans le déploiement. Les démos à identités/tokens synthétiques ne fournissent pas ce profil. Voir [sécurité de déploiement](deployment-security.md).

Consommation indépendante et métadonnées locales sont qualifiées. Version officielle, droits de namespace/registre et signature ne sont pas prouvés par ce staging et restent à vérifier sans publier. Le namespace actuel est io.github.gear4jtest ; Java 17 est requis ; npm 0.6.2 prévoit un dist-tag expérimental. Aucune version officielle ni migration de namespace n'est décidée ici.

Les APIs Studio restent @Experimental ; pas de baseline publiée Studio N-1 pour Japicmp. Contrats et consommateurs du candidat sont testés ; la politique de compatibilité des APIs stables Gear4J reste inchangée. HTTP est en version 2 ; modèle déclaratif et profil de simulation en version 1.

Limites acceptables pour une première release expérimentale : APIs évolutives documentées, migration depuis la baseline P6 exacte, Chromium, SSE best-effort avec relecture du snapshot, simulation déclarative sans code métier. Le vrai dry-run/isolation runtime reste différé. Les gates NVD/PostgreSQL/navigateur manquants ne sont pas des limites acceptées.

## Décision

- **SDK/composants intégrables : NO-GO pour publier aujourd'hui.** Correctifs intégrés et tests applicatifs réussis ; lever les gates restants avec leurs preuves sur le commit candidat. Ce verdict porte sur la qualification, sans remise en cause de l'architecture.
- **Studio clé en main de production : NO-GO.** Même après les gates SDK, l'intégration hôte et la sécurité du déploiement réel restent à qualifier.

Message de commit proposé :

    fix(studio): harden release gates and remediate JVM dependencies

    Align PostgreSQL qualification contexts and require native v17 clients.
    Fix workflow invocation and container health checks; enforce security gates.
    Make loading retry qualification deterministic and cover demo wiring.
    Upgrade Jackson and constrain test-only Commons Compress.
    Document executed checks, evidence limits and remaining release blockers.
