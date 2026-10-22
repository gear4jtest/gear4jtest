# Gear4J Studio — Release Readiness

La stabilisation P6 est implémentée. **L’implémentation du gate ne vaut pas qualification réussie.** Le bilan exécuté, ses preuves et les réserves figurent dans [verification-release-readiness.md](verification-release-readiness.md). Aucune publication officielle n’est déclenchée.

| Chantier | Livrable | Vérification |
| --- | --- | --- |
| Contrats | [Contrats publics et baseline](release-contracts.md), manifeste SQL et protocole | `studioReleaseBaseline`, consommateurs indépendants |
| Données | Restauration H2 peuplée ; qualification PostgreSQL dédiée, DDL échoué/reprise, checksums, concurrence au redémarrage | `StudioBackupRestoreTest`, `studioPostgresQualification` |
| PostgreSQL réel | Service PostgreSQL 17 et outils natifs de sauvegarde dans un job CI sans skip | `:gear4jtest-studio-jdbc:studioPostgresQualification` |
| Navigateur | Cinq parcours sur distributions Java, Spring, composants embarqués et standalone | `studioBrowserTest` |
| Distribution | JAR/POM/sources/Javadoc, lanceurs installables, consommateurs Java/Spring indépendants, paquet npm | `verifyStagedReleaseArtifacts`, `studioConsumerSmoke`, `studioPackageCheck` |
| Sécurité | HTTPS/mTLS réel, identités scoppées, credentials, refus ACL/targets et révocation navigateur | [Conditions de déploiement](deployment-security.md) |

Prérequis : Java 17, Python 3, Node conforme à `gear4jtest-studio-web/package.json`, PostgreSQL 17 jetable et `pg_dump`/`pg_restore` 17 disponibles dans PATH. Installer les dépendances verrouillées puis Chromium :

```sh
cd gear4jtest-studio-web
npm ci --ignore-scripts --no-audit --no-fund
npx --no-install playwright install --with-deps chromium
cd ..
./gradlew spotlessApply
./gradlew studioReleaseReadiness --continue
```

Le service PostgreSQL doit être exclusivement destiné à la qualification. Fournir `GEAR4J_TEST_DATABASE_DISPOSABLE=true`, `GEAR4J_TEST_POSTGRES_URL`, `GEAR4J_TEST_POSTGRES_USER` et `GEAR4J_TEST_POSTGRES_PASSWORD` via le mécanisme de secrets de l’hôte. Le test crée et supprime son propre schéma aléatoire ; il n’utilise jamais une base métier. Il échoue si la configuration est absente. Aucun fallback H2, aucune assumption JUnit ne peut le rendre vert sans PostgreSQL. `check` conserve les tests Testcontainers existants et peut les ignorer sans Docker : il ne remplace donc pas ce gate.

Le workflow `.github/workflows/studio-release-readiness.yml` installe explicitement les outils PostgreSQL 17 depuis le dépôt officiel PGDG, en plus du service. Le déclarer obligatoire dans la protection des branches reste une action du mainteneur ; ajouter un workflow au dépôt ne configure pas cette protection. Le job n’a pas été exécuté sur GitHub dans cette livraison.

Le navigateur peut être fourni par `STUDIO_BROWSER_EXECUTABLE` pour un environnement contraint. La version réellement utilisée est conservée dans le rapport. La matrice qualifiée ici est Chromium ; Firefox/WebKit et les configurations de proxy/IdP de l’organisation doivent faire l’objet d’une qualification distincte.

## Baseline et migrations

`config/studio-release-baseline.json` gèle les V1 H2/PostgreSQL du candidat P6, sans changer leur contenu. La base peuplée de **cette baseline exacte** est le point de départ supporté pour la stabilisation. Les anciennes V1 de prototypes P1–P5 ne constituent pas un chemin d’upgrade automatique : un checksum différent est refusé. Ne pas modifier la table d’historique pour contourner ce refus. Une migration de données de prototype exige inventaire, sauvegarde et procédure spécifique avant adoption ; elle n’est pas livrée implicitement par une V2 vide.

Après publication d’une baseline, toute évolution persistée devra ajouter une migration incrémentale et un test sur une base de la release précédente. Le manifeste et les formats persistés doivent être revus ensemble. Ne jamais « réparer » le checksum d’une migration déjà appliquée.

Exécuter un **seul job de migration initiale**, avant ouverture du trafic et démarrage des réplicas. La concurrence qualifiée porte sur le redémarrage d’une baseline déjà initialisée. Elle ne promet pas que plusieurs créations concurrentes des tables d’infrastructure sur une base vierge sont sûres. Après un DDL échoué, inspecter la cause et l’état réel, rétablir les prérequis puis utiliser la procédure explicite `prepareRetry` avec le même SQL/checksum ; ne pas relancer aveuglément.

La restauration H2 utilise le backup natif, puis une autre base fichier. La qualification PostgreSQL utilise `pg_dump` et `pg_restore`, compare les lignes de toutes les tables du schéma et vérifie l’ancrage de contexte. Sauvegardes, permissions, historique et captures sont des données sensibles : les preuves CI excluent les bases, dumps et logs de session.

## Publication séparée

`stageMavenCentral` écrit un dépôt **local** sous `build/staging-deploy`. Les consommateurs propres résolvent ces coordonnées Maven sans dépendre des projets sources. `npm pack` construit un paquet local. Aucun upload Maven/npm, tag ni release officielle n’est effectué par `studioReleaseReadiness`.

Ce gate complète les contrôles généraux du dépôt (dépendances, licence, métadonnées, signature et publication) ; il ne les remplace pas. Le choix du numéro public, du registre, des signatures et de la provenance CI appartient à la décision de release. `1.0.0-SNAPSHOT` reste le défaut Maven ; le composant expérimental passe à `0.6.2` pour les corrections navigateur.

P6 livre la simulation déclarative sans code métier réel. Le dry-run exécuté dans le moteur Gear4J, l’isolation avancée des effets et la convergence de déploiement restent différés. Aucun nouvel axe produit n’est lancé par cette phase.

## Gate de publication et preuves

La [qualification PostgreSQL](postgresql-qualification.md) est appelée comme workflow réutilisable obligatoire par `release.yml`, sur le même commit et avec la version candidate. Le check doit aussi être rendu obligatoire dans la protection des branches. `studioDependencyAudit` vérifie les dépendances npm (outils inclus), refuse un audit incomplet ou des résultats high/critical, et conserve le JSON. `studioReleaseReadiness` produit un manifeste PASS seulement après réussite réelle de PostgreSQL, des cinq parcours navigateur et de l'audit. Voir la [maturité](release-maturity.md) et le [bilan actualisé](verification-release-closure.md).

The JVM dependency audit also runs on pull requests and pushes. It requires an NVD API key or an explicitly configured data feed and fails early if neither is available. The Studio qualification and the npm audit do not replace this mandatory JVM release gate.
