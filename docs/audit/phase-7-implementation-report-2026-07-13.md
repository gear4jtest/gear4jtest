# Rapport d'implémentation - Phase 7

Date : 13 juillet 2026
Périmètre : stabilisation avant 1.0, découplage des modules, typage des contrats, compatibilité et clôture documentaire.

## Résultat

La phase 7 est implémentée dans l'arbre cumulatif. Les constats F03, F04, F10, F38, F41, F42, F43 et F47 disposent
maintenant d'une correction source, de tests ou d'un contrôle de build/documentation explicite. La qualification finale
reste conditionnée à l'exécution du build Gradle connecté, de Spotless, de la matrice Testcontainers et du dry-run de
publication.

## Changements réalisés

### Découplage de l'API externe

- Création du module optionnel `gear4jtest-external-jdbc`.
- Déplacement des repositories JDBC externes, du migrateur de schéma, du stockage d'artefacts en base et du provider
  `ServiceLoader` vers ce module.
- Suppression des dépendances JDBC et Jackson de `gear4jtest-external-api`.
- Conservation des contrats, stores mémoire/fichiers, traducteurs, compilateur et classloader dans le module neutre.
- Ajout d'un guide de migration pré-1.0 pour les coordonnées et packages déplacés.
- Ajout du nouveau module au smoke test consommateur publié.

### Contrats Java typés

- Passage à `RunRequest<IN>` sur l'API d'exécution, les extensions et la chaîne interne.
- Conservation de l'usage ergonomique et typé `RunRequest.builder().input(value).build()` grâce au rétrécissement du
  type du builder.
- Passage à `GeneratedAssemblyLine<IN, OUT>` et propagation des wildcards dans le chargement dynamique.
- Génération XML de l'interface avec les vrais types d'entrée et de sortie, y compris les sorties conteneurs imbriquées.
- Ajout de tests de contrat et d'un smoke test de compilation consommateur sans cast.

### Refactorings incrémentaux

- Extraction de `BaselineSchemaValidator` hors de `JdbcSchemaMigrator`.
- Extraction de `EventSubscriptionResolver` hors de `EventManager`.
- Conservation du découpage XML existant entre validation, parsing, résolution de types et rendu Java.
- Aucune réécriture globale du runtime.

### Observabilité événementielle

- Ajout de `EventRuntimeMetrics` et `ProcessEventRuntimeStats` pour une agrégation JVM sans tags.
- Comptage des runtimes actifs, événements en file, réactions en vol, publications, dispatchs, drops, échecs et rejets du
  dispatcher partagé.
- Mesure des échantillons et des latences moyenne/maximale entre mise en file et dispatch.
- Binding Micrometer global et automatique dans le starter Spring Boot.
- Test de non-fuite des jauges après arrêt et test des séries tagless.

### Build et compatibilité

- Wrapper Gradle mis à niveau vers 9.6.1 avec checksum de distribution et wrapper JAR vérifiés.
- Plugin JMH mis à niveau vers 0.7.3.
- Ajout de contrôles CI stricts du configuration cache et des warnings Gradle.
- Ajout de `apiCompatibilityCheck` basé sur Japicmp 0.26.1.
- Comparaison N-1 obligatoire pour toute version stable postérieure à 1.0.0 ; exclusion des API `@Internal` et
  `@Experimental`.
- Transmission optionnelle de la baseline depuis le workflow de release ou `GEAR4J_API_BASELINE_VERSION`.

### Frontières et documentation

- Extension du test de stabilité des packages à tous les modules Java publiés.
- Ajout des marqueurs manquants dans Jackson, Micrometer, Spring et Spring Boot.
- Suppression des doublons d'ADR et de documentation d'architecture.
- Canonisation du media type XML `application/vnd.gear4j.assembly-line+xml`.
- Ajout de la politique de compatibilité 1.x et de l'ADR 0023.
- Mise à jour de la matrice de clôture, des guides de release, de sécurité, d'architecture et d'observabilité.

## Corrections détectées pendant la validation

- Restauration de l'import `ArrayList` dans `JdbcSchemaMigrator` après extraction.
- Restauration de l'import `ArtifactStoreStats` dans le module JDBC externe.
- Ajout de trois `package-info.java` racine détectés par le nouveau test global.
- Correction de l'inférence de `RunRequest.builder().input(...)`, qui produisait initialement un `RunRequest<Object>`.
- Correction de l'assertion XML pour une sortie réelle `List<List<String>>`.
- Correction de références documentaires obsolètes au media type et à l'ancien emplacement JDBC/Jackson.
- Correction de l'identifiant Gradle JMH : `me.champeau.jmh` remplace l'identifiant inexistant
  `me.champeau.gradle.jmh` dans la déclaration et l'application du plugin.
- Alignement des implémentations de test de `RunInterceptorExtension` sur `RunRequest<IN>` et des hooks
  `AbstractRunHooksExtension` sur `RunRequest<?>`, afin de supprimer les collisions d'érosion et les faux overrides.
- Marquage explicite de `EventSubscriptionResolver` comme type `@Internal` et correction du contrôle de frontière pour
  reconnaître aussi les types internes package-private, sans élargir artificiellement la baseline API/SPI.
- Ajout d'un manifeste de nettoyage idempotent pour les mises à jour par superposition d'archive. Il supprime les 36
  anciens fichiers déplacés ou retirés par la phase 7, qu'un format ZIP ne peut pas effacer lors de l'extraction, sans
  accéder à `.git` ni aux fichiers locaux non concernés.
- Suppression du setter Groovy personnalisé de la propriété Gradle `outputDir` dans
  `XmlAssemblyLineGeneratorExtension`. Sous Gradle 9, la décoration de l'extension génère déjà cet adaptateur DSL ; le
  setter déclaré `setOutputDir(Object)` créait une méthode dupliquée et empêchait l'application du plugin avant les tests.
- Déclaration explicite de `XmlAssemblyLineGenerateTask` comme `@CacheableTask`. Ses fichiers XML, son media type, son
  mode de confiance et son répertoire généré étaient déjà modélisés par les annotations Gradle ; Gradle 9 exige en plus
  que la décision de mise en cache soit exprimée sur le type de tâche.

## Validation exécutée

Validations réussies :

- parsing Java 17 de l'ensemble des sources de production et de test ;
- compilation Java 17 complète de `gear4jtest-core` ;
- compilation Java 17 complète de `gear4jtest-jdbc` ;
- compilation Java 17 de `gear4jtest-external-api`, hors implémentation JDT remplacée localement par un stub de
  validation faute d'artefact JDT résolu ;
- compilation Java 17 complète du nouveau `gear4jtest-external-jdbc` ;
- compilation Java 17 de `gear4jtest-xml`, hors formateur JDT remplacé localement par un stub de validation ;
- compilation Java 17 complète de `gear4jtest-micrometer` ;
- smoke test de typage `RunRequest`/`GeneratedAssemblyLine` ;
- smoke test du cycle publication/dispatch/réaction/arrêt et des métriques JVM ;
- génération et compilation Java d'une assembly line XML typée ;
- découverte `ServiceLoader` des stores `MEMORY`, `FILESYSTEM` et `DATABASE` ;
- vérification de tous les `package-info.java` et de l'unicité des marqueurs ;
- parsing YAML des workflows CI et release ;
- contrôle des checksums Gradle 9.6.1 ;
- simulation d'une extraction cumulative sur l'archive source initiale : les 36 fichiers obsolètes sont détectés puis
  supprimés, tandis qu'un fichier local hors manifeste et un témoin placé dans `.git` sont préservés.

Limitation de l'environnement : le build Gradle complet n'a pas pu résoudre le plugin Spotless depuis le Gradle Plugin
Portal. Il ne doit donc pas être déclaré validé localement. Le plugin et la version configurée existent ; le blocage est
la résolution réseau de cet environnement.

## Qualification obligatoire sur le poste cible

```bash
./gradlew spotlessApply
./gradlew help --configuration-cache --configuration-cache-problems=fail --warning-mode=all
./gradlew clean build
./gradlew verifyPerformanceBudgets
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=all
./gradlew releaseCheck -PprojectVersion=1.0.0-rc1
PROJECT_VERSION=1.0.0-rc1 JRELEASER_DRY_RUN=true ./gradlew jreleaserDeploy
```

À partir de 1.0.1 :

```bash
./gradlew apiCompatibilityCheck \
  -PprojectVersion=1.0.1 \
  -Pgear4j.apiBaselineVersion=1.0.0
```

## Risques résiduels assumés

- F18 reste accepté pour le MVP : verrouillage et vérification des dépendances ne sont stricts qu'en opt-in.
- F31 reste mitigé : le contrôle de cohérence détecte les références inter-store incohérentes sans imposer une FK
  impossible sur des stores polymorphes.
- JPMS reste volontairement hors de cette phase ; les noms de modules automatiques sont conservés.
- Les seuils de performance, de couverture, de santé et les dialectes doivent être calibrés sur la première CI connectée.

## Conclusion

Le code source de la phase 7 est prêt pour qualification. La publication 1.0 ne doit être autorisée qu'après passage des
commandes ci-dessus, inspection des POM/JAR générés et réussite du dry-run Maven Central/JReleaser.
