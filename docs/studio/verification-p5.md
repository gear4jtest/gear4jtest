# Vérification P5 — 7 octobre 2026

Base : archive utilisateur `gear4jtest-studio-p41(1).zip`, SHA-256
`2692d29991427ab415e79fc62ecfc887c981e0639588ffab36a52d5d13e0cd49`.
La comparaison des sources limite les changements aux modules Studio, leur
documentation, leurs distributions et leur intégration Gradle. Aucun changement
du moteur Gear4J ni des migrations SQL. Les deux nouveaux modules possèdent
leur seuil de couverture de lignes à 0,65 ; les seuils existants et le budget
de suppressions ne sont pas abaissés.

Environnement : JDK Temurin 17.0.20.1+1, Gradle 9.6.1 correspondant au wrapper,
Node 24.19.0, npm 11.9.0. Mockito chargé au démarrage avec le
[script existant](verification-support/mockito-startup.init.gradle), faute
d’attachement dynamique dans cet environnement.

## Résultats Studio

| Module | Réussis | Ignorés |
| --- | ---: | ---: |
| studio | 28 | 0 |
| studio-runtime | 20 | 0 |
| studio-jdbc | 7 | 1 |
| studio-json | 2 | 0 |
| studio-demo | 14 | 0 |
| studio-http | 11 | 0 |
| studio-spring-demo | 2 | 0 |
| studio-standalone | 8 | 0 |

**92 tests Java Studio réussis**, aucun échec ni erreur. Les onze tests HTTP
ont été déplacés de demo vers http ; deux tests de câblage du serveur et de
la politique propres à demo complètent cette extraction. Les **36 tests
frontend jsdom** passent.

Comportements ajoutés ou explicitement exercés :

- Texte synthétique avec espaces et retour à la ligne exécuté exactement ;
  aucun appel du résolveur. Admission de la politique conservée.
- Empreinte liée à l’input résolu, comparaison sous la même clé, dérive du
  contenu, rotation d’identifiant de clé, conflit du reçu et JSON durable H2.
  READ_RUNS seul ne donne pas accès à la provenance de fixture ; READ_SCENARIOS
  reste requis. Aucune capture IN/OUT pour une référence.
- Deux vraies applications HTTP, Java et Spring, avec deux bases H2 distinctes,
  pilotées par la même passerelle. Création, draft, scénario, test contrôlé,
  version figée, publication TEST, promotion RUN, reçu et historique des runs.
- Inventaire filtré, catalogue/contexte propres à chaque cible, sujets éditeur
  et viewer distincts, absence d’URL/credential distant dans l’inventaire.
- Mauvais contexte, mauvais subject, protocole incompatible, révocation locale
  après handshake : aucune mutation envoyée. Préconditions de contexte
  vérifiées aussi par le backend et contre un ancien contexte de navigateur.
- Réponse perdue après acceptation : résultat inconnu, une seule soumission.
  Coupure sur PUT après lecture du corps : aucune seconde émission du corps.
- Timeout, réponse trop volumineuse, redirection sans propagation de token,
  chemins arbitraires/encodés, corps trop volumineux et configuration ambiguë.
- Changement de cible : nouvelles instances, saisies/ACL/scénarios protégés,
  action en cours bloquante, réponse de connexion tardive ignorée, révocation
  encore traitée après une connexion échouée, déconnexion et inventaire périmé.
- SSE distant non relayé (501) et GET du snapshot durable toujours disponible.

## Contrôle global et distribution

Le contrôle global recense **1 504 tests de modules : 1 499 réussis,
5 ignorés, aucun échec ni erreur**. Les trois tests de conventions build-logic
passent également, hors de ce total.

Les huit contrôles de couverture Studio passent. Le contrôle critique historique
reste en échec sur `MigrationLockStore` : couverture de branches 0,4, minimum
0,5. Cette classe et ce seuil sont inchangés. Ce blocage est déjà documenté dans
P3/P4/P4.1 dans l’environnement sans Docker ; ne pas présenter le `check` global
comme vert.

Les intégrations ignorées nécessitent Docker : JdbcSchemaMigratorFatalRollbackIT,
SimpleChainBuilderDataSourceIT, DatabaseAssemblyRunRepositoryMultiDialectIT,
ExternalJdbcMultiDialectIT et PostgreSqlStudioStoreIT. PostgreSQL n’est donc
pas qualifié par une exécution ici. Les scénarios distants utilisent H2.

Spotless, Checkstyle, les autres contrôles de couverture par module, les
contrôles d’architecture/documentation et le budget de suppressions passent.
La dernière exécution de `check --continue` ne signale que l’échec critique
historique ci-dessus. Une erreur transitoire du cache Spotless a disparu après
réexécution des tâches de formatage ; aucune configuration n’a été affaiblie.

`installDist` et `pack:check` passent. Le launcher Unix construit a été démarré
avec un fichier de configuration temporaire et des tokens synthétiques :
l’éditeur voit deux cibles, le viewer une seule, et les assets sont servis.
Le processus et ses ressources sont ensuite arrêtés. Ce smoke test complète
les tests fonctionnels distants, sans prétendre qualifier le launcher Windows.
Tous les fichiers du `.tgz` frontend livré correspondent aux sources courantes.

## Reproduire

```sh
./gradlew spotlessApply
./gradlew --init-script docs/studio/verification-support/mockito-startup.init.gradle check --continue
./gradlew :gear4jtest-studio-standalone:installDist
npm --prefix gear4jtest-studio-web test
npm --prefix gear4jtest-studio-web run pack:check
```

Le script Mockito est facultatif sur un poste autorisant l’attachement dynamique.
Relancer les intégrations avec Docker pour compléter la qualification. Le
wrapper complet et le bit exécutable de `gradlew` sont conservés dans l’archive.
Le frontend consommable livré est `distributions/gear4j-studio-web-0.5.0.tgz` ;
l’ancien paquet reste dans la livraison P4.1. Aucune publication Maven/npm ni
aucun déploiement externe n’a été effectué.

Aucun passage Playwright ou contrôle visuel navigateur n’est revendiqué : le
binaire Chromium attendu n’est pas installé ici. Les tests DOM sont ceux de
jsdom, les tests distants exécutent de vrais serveurs locaux Java/Spring.
Le launcher reste loopback ; SSO, ingress de production, multi-instance et
simulation ne sont pas qualifiés par P5. Voir le [guide](p5.md) pour démarrer,
intégrer les adaptateurs et comprendre les limites.
