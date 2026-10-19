# Gear4J Studio — bilan Release Readiness

Qualification du 13 octobre 2026. **NO-GO pour une publication officielle.** Les correctifs de stabilisation et les gates sont livrés ; le gate obligatoire PostgreSQL reste en échec faute de service configuré. Aucune publication Maven/npm, aucun tag ni déploiement officiel n’a été déclenché.

## Résultats exécutés

| Contrôle | Résultat | Preuve / portée |
| --- | --- | --- |
| `check` de tous les modules, style, format et couverture | PASS | L’agrégat sélectionne explicitement chaque `:check`, pas seulement la racine. Le seuil critique de `MigrationLockStore` est conservé. |
| Tests Java des modules | 1 537 réussis, 5 ignorés, aucun échec | 1 542 cas recensés ; trois tests `build-logic` réussis en complément. |
| Sous-ensemble Studio | 127 réussis, 1 ignoré | Inclus dans le total précédent ; le test ignoré nécessite Docker/PostgreSQL. |
| Frontend | 42/42 réussis | Test de régression de révocation inclus. |
| Navigateur réel | 5/5 parcours réussis | Java, Spring, composants embarqués dans chacun, standalone fédéré ; Chromium 151.0.7922.34. |
| Sauvegarde/restauration H2 peuplée | PASS | XML SOURCE exact, scénario, rapport de simulation, version, run détaillé, ACL ; checksum incompatible et ré-ancrage de contexte refusés. |
| HTTPS/mTLS et sécurité | PASS sur les profils testés | Vrais échanges TLS, identité scoppée, autorisations, rotation de token, rejet de certificat absent, CA inconnue et hostname incorrect ; matrice détaillée dans le guide de déploiement. |
| Artefacts Maven | PASS | Dépôt local, JAR/POM/sources/Javadoc et fichiers légaux vérifiés ; consommateurs indépendants Java simple et Spring réussis. |
| Paquet npm | PASS | `@gear4j/studio-web@0.6.2` réellement installé dans un consommateur temporaire ; tous les exports publics importés. Le `.tgz` livré correspond aux sources. |
| Baseline SQL / protocole | PASS | V1 H2/PostgreSQL inchangées, hashes gelés, protocole HTTP 2. Aucun changement de Java de production. |
| PostgreSQL obligatoire | **FAIL — non qualifié** | Un test en échec, zéro skip : absence de configuration explicite du service jetable. Aucune requête de qualification PostgreSQL n’a été exécutée. |

Les cinq tests ignorés par les suites ordinaires sont `ExternalJdbcMultiDialectIT`, `JdbcSchemaMigratorFatalRollbackIT`, `DatabaseAssemblyRunRepositoryMultiDialectIT`, `SimpleChainBuilderDataSourceIT` et `PostgreSqlStudioStoreIT`. Le gate PostgreSQL distinct empêche ces skips de donner un faux feu vert de release.

Les parcours navigateur couvrent création domaine/chaîne/draft, validation, persistance d’un XML invalide puis reprise après rechargement, conversion explicite, test contrôlé, version immuable, publication TEST et promotion RUN, scénario et substitutions persistées, simulation réussie et relecture du rapport. Ils vérifient aussi les refus d’accès, une révocation réelle et l’effacement de l’éditeur, le changement de target, l’absence de tokens dans les stockages navigateur et les logs des lanceurs, les snapshots SSE sur reconnexion directe, et l’absence de run métier supplémentaire pour une simulation. Les captures desktop et mobile sont jointes ; le focus et l’activation clavier sont vérifiés sur la connexion.

La simulation est explicitement vérifiée `SUCCEEDED` après enregistrement et relecture de substitutions `PASSTHROUGH`. La simple présence d’un rapport, qui pourrait contenir un échec synthétique volontaire, ne suffit pas au test.

## Correctifs et livrables

- Liaison du `fetch` natif au contexte navigateur : suppression de l’erreur `Illegal invocation`.
- Pattern HTML des clés compatible avec le mode Unicode Sets des navigateurs récents.
- Formulaires et panneau des versions contenus sur petit écran, y compris avec des identifiants longs ; aucun masquage du débordement pour faire passer le test.
- Purge de la sélection et des données associées après refus de relecture d’une ressource révoquée ; conservation du travail courant lors d’une navigation infructueuse vers une autre ressource.
- Trois tests ciblés de `MigrationLockStore` pour ses chemins d’échec et de concurrence, sans diminuer les seuils.
- Test natif de restauration H2, qualification PostgreSQL 17 sans fallback, test HTTPS/mTLS, suite navigateur et consommateurs d’artefacts.
- Distributions de lancement Java/Spring, gate Gradle global, workflow CI dédié, baseline et revue des contrats publics, conditions de sécurité documentées.

## Réserves restantes et décision

1. **PostgreSQL réel doit encore passer.** Cet environnement ne fournit ni Docker ni serveur PostgreSQL accessible ; seuls les identifiants système root sont disponibles et aucune configuration de base jetable n’a été fournie. Le test ajouté est compilé, mais ses assertions SQL, sa contention, sa reprise après DDL échoué et son `pg_dump`/`pg_restore` restent à exécuter. Le workflow CI fournit PostgreSQL 17 et ses outils clients ; il n’a pas été exécuté sur GitHub ici. Le rendre obligatoire dans la protection des branches appartient au mainteneur.
2. **Le profil de sécurité de production reste à qualifier.** Les tests utilisent des hôtes locaux et credentials synthétiques. Aucun IdP OIDC d’entreprise, proxy corporate, coffre ou certificat de production n’a été fourni. Les validations issuer/audience/expiration/signature, la rotation des clés, le périmètre réseau et les deadlines du provider de credentials doivent être vérifiés sur le profil retenu. Les lanceurs de démonstration ne constituent pas ce profil.
3. **Périmètre de migration précis.** La baseline P6 exacte est préservée ; aucun upgrade universel depuis les anciennes V1 expérimentales n’est revendiqué. Un checksum ancien est refusé. Une première initialisation doit être effectuée par un job unique ; la concurrence couverte par la qualification PostgreSQL vise les redémarrages après initialisation. H2 ne prouve pas le comportement PostgreSQL.
4. **Matrice UX bornée.** Chromium seulement ; pas de qualification Firefox/WebKit, ni audit complet d’accessibilité. Les diagnostics de drift historique restent couverts par les tests Java et composants P6 ; aucun ancien runtime n’est reconstruit dans le navigateur. Le test SSE porte sur une reconnexion avec snapshot, pas sur un replay durable.
5. **Publication à décider séparément.** La signature, le namespace public, les credentials de registre, les gates généraux de release et l’analyse actualisée des vulnérabilités ne sont pas certifiés par cette phase. Le staging réalisé est local. Gradle est fixé à la version du wrapper ; les avertissements de compatibilité Gradle 10 ne sont pas résolus ici.

Les correctifs sont prêts pour revue et intégration. La décision de publication reste **NO-GO**, principalement parce que PostgreSQL réel n’a pas été qualifié. Lever ce point exige une exécution réussie du gate sur un service PostgreSQL 17 jetable, puis l’examen du profil de déploiement et des conditions générales de release. Aucune nouvelle phase fonctionnelle n’est lancée.

## Reproduction et preuves

Voir [release-readiness.md](release-readiness.md) pour les prérequis et les variables de qualification, [deployment-security.md](deployment-security.md) pour la matrice de sécurité et [release-contracts.md](release-contracts.md) pour les API/SPI et formats.

```sh
./gradlew spotlessApply
./gradlew studioReleaseReadiness --continue
```

Dans cet environnement, les tests Mockito ont utilisé l’agent au démarrage via `-I docs/studio/verification-support/mockito-startup.init.gradle` car l’attachement dynamique est indisponible. Chromium a été fourni par `STUDIO_BROWSER_EXECUTABLE` ; aucune assertion métier ou sécurité n’a été désactivée. La commande finale échoue uniquement sur `:gear4jtest-studio-jdbc:studioPostgresQualification`.

Preuves conservées dans [evidence-release-readiness/junit-summary.json](evidence-release-readiness/junit-summary.json), [gradle-gate.txt](evidence-release-readiness/gradle-gate.txt), [browser-result.json](evidence-release-readiness/browser-result.json), [source-review.json](evidence-release-readiness/source-review.json) et [npm-package.json](evidence-release-readiness/npm-package.json). Les dumps, bases de test, credentials et logs de sessions ne sont pas inclus.

Message de commit :

```text
chore(studio): add release readiness gates and harden browser integration
```
