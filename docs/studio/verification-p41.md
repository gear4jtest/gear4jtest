# Vérification P4.1 — 6 octobre 2026

Base : livraison P4, SHA-256
`1d57184bfbcb7d57b0a11a933b65634f19239c7759bbcdaed38870818f6345b7`.
La comparaison avec cette archive limite les modifications aux modules Studio,
à leur distribution et à la documentation. Le moteur Gear4J, les seuils de
couverture et le budget de suppressions sont inchangés.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1, Node 24.19.0, npm 11.9.0.
La distribution Gradle utilisée correspond à la version et au checksum du
wrapper. Mockito est chargé au démarrage via le
[script existant](verification-support/mockito-startup.init.gradle), car
l’attachement dynamique n’est pas disponible dans cet environnement.

## Résultats Studio

| Module | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 25 | 0 |
| studio-runtime | 20 | 0 |
| studio-jdbc | 7 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 23 | 0 |
| studio-spring-demo | 2 | 0 |

**79 tests Java Studio réussis**, aucun échec ni erreur. L’intégration PostgreSQL
est compilée mais ignorée faute de Docker. Les **31 tests frontend jsdom** et
`pack:check` passent. Le paquet public est construit, extrait et consommé sans
dépendance à la démo ; les fichiers du `.tgz` livré correspondent aux sources.

Les vérifications ajoutées ou étendues couvrent notamment :

- Exécution métier et snapshot final terminés alors que le sink intermédiaire
  est bloqué par un latch ; saturation bornée du dispatcher et réutilisation
  des places libérées, sans file d’événements par callback.
- Mise à jour d’un run indépendant pendant qu’une autre transaction conserve
  le verrou de domaine et celui d’un autre run ; état terminal non écrasable.
- Marquage de fraîcheur sans changement du résultat métier, effacement par
  observation tardive, délai de grâce et purge explicite des observations stale.
- Séparation des métriques pour un même nodeId selon version, mode, artefact,
  catalogue, build et identité non attestée.
- Scénarios persistés après réouverture H2, révisions immuables, conflits
  optimistes, pagination, archivage, intégrité du contenu et audit sans fixture.
- Permissions spécifiques et portée privée d’un draft, refus d’une cible
  étrangère, d’une SOURCE ou d’une version inconnue.
- Politique d’admission absente, secret refusé, référence révoquée et ACL
  modifiée pendant la résolution ; aucun run admis dans ces cas.
- Identité de reçu liée aux révisions exactes du scénario et de la cible,
  provenance durable, assertions et statut NOT_EVALUATED après échec de
  préparation ; pas de nouvelle exécution lors d’une relecture du reçu.
- Assertions sur la vraie sortie avant redaction ; captures IN/OUT désactivées
  pour les fixtures référencées, sans copie des valeurs dans le verdict.
- Routes HTTP Java et Spring, exécution de l’artefact d’une version figée alors
  que le draft a changé, champs inconnus refusés et contrôle des permissions.
- Composant public : brouillon local conservé, cible sale refusée, révisions
  exactes, effacement après refus d’accès et GET explicite après réponse perdue,
  sans répétition automatique du POST.

## Contrôle global

Le `check --continue` recense **1 491 tests de modules : 1 486 réussis,
5 ignorés, aucun échec ni erreur**. Les trois tests de conventions de build-logic
passent également, hors de ce total.

Spotless, Checkstyle, les six contrôles de couverture Studio, les autres
contrôles de couverture par module, les contrôles d’architecture/documentation
et le budget de suppressions passent.

**Le contrôle global n’est pas vert.** Son unique tâche en échec reste
`jacocoCriticalCoverageVerification` : `MigrationLockStore`, branches 0,4 pour
minimum 0,5. Ce blocage est déjà documenté dans P3/P4 dans l’environnement sans
Docker. La classe et son seuil n’ont pas été modifiés. Aucun contrôle n’a été
désactivé ou affaibli.

Les cinq intégrations ignorées nécessitent Docker :
JdbcSchemaMigratorFatalRollbackIT, SimpleChainBuilderDataSourceIT,
DatabaseAssemblyRunRepositoryMultiDialectIT, ExternalJdbcMultiDialectIT et
PostgreSqlStudioStoreIT. Ce dernier couvre maintenant aussi les scénarios,
la provenance d’audit et la maintenance de fraîcheur ; son comportement
PostgreSQL n’est pas qualifié par une exécution ici.

## Reproduire

```sh
./gradlew spotlessApply
./gradlew --init-script docs/studio/verification-support/mockito-startup.init.gradle check --continue
npm --prefix gear4jtest-studio-web test
npm --prefix gear4jtest-studio-web run pack:check
```

Le script Mockito est facultatif sur une machine autorisant l’attachement
dynamique. Relancer les intégrations avec Docker pour compléter la qualification.
Le wrapper complet est conservé dans l’archive, avec `gradlew` exécutable.

Aucun contrôle visuel navigateur ni passage Playwright n’est revendiqué : les
tests UI sont ceux de jsdom. Les scénarios restent des tests contrôlés réels,
pas une isolation ou une simulation P6.

Le paquet livré est `distributions/gear4j-studio-web-0.4.1.tgz`, remplaçant le
paquet 0.4.0 dans cette livraison ; celui-ci reste dans l’archive P4. Aucune
publication Maven/npm ni aucun déploiement externe n’a été effectué.

**Utiliser une nouvelle base de développement P4.1 et conserver la base P4** :
les migrations V1 sont consolidées avant la première release, conformément au
choix du projet. Aucune base existante n’a été supprimée ou migrée automatiquement.
