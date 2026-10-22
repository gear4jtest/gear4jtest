# Gear4J Studio — maturité et installation du candidat

Le candidat est un **SDK expérimental de management et de composants intégrables**. Il n'est pas un backoffice de production préconfiguré. Aucune publication officielle n'est effectuée par cette qualification.

| Livrable | Contrat qualifié / responsabilité de l'hôte |
| --- | --- |
| Modules Java Studio | APIs expérimentales, Java 17, intégration framework-neutral ; l'hôte fournit identité, autorisations initiales, catalogue autorisé, stockage et exécuteurs. |
| Composants web | ESM / Web Components, transport et acquisition du token injectés par le portail ; les contrôles d'autorisation restent côté serveur. |
| Démonstrations Java et Spring | Distributions locales de qualification, tokens synthétiques ; publications Maven désactivées. Aucun starter OIDC/Spring Security de production n'est livré. |
| Standalone | Contrats fédérés et lanceur de démonstration ; l'hôte doit intégrer son IdP, les credentials par target et sa configuration HTTPS/proxy. |

## Consommation indépendante

Les coordonnées actuelles appartiennent au namespace de travail `io.github.gear4jtest` ; elles ne constituent pas une décision de namespace officiel. Une éventuelle adoption de `io.github.gear4j` doit être coordonnée (group, packages/module names, plugin ids, guides, consommateurs, registre), puis requalifiée avant publication, sans renommage implicite dans ce candidat.

Depuis les sources, produire et vérifier le dépôt Maven local :

```sh
./gradlew stageMavenCentral studioConsumerSmoke
```

Dans un projet Java 17 indépendant, utiliser le dépôt produit uniquement pour le groupe de travail :

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = uri('/chemin/gear4j/build/staging-deploy') } }
        filter { includeGroup 'io.github.gear4jtest' }
    }
    mavenCentral()
}
dependencies {
    implementation 'io.github.gear4jtest:gear4jtest-studio-runtime:1.0.0-SNAPSHOT'
    implementation 'io.github.gear4jtest:gear4jtest-studio-jdbc:1.0.0-SNAPSHOT'
    implementation 'io.github.gear4jtest:gear4jtest-studio-http:1.0.0-SNAPSHOT'
    // Le pilote JDBC et le profil de sécurité appartiennent à l'application.
}
```

Exemples exécutables : [PlainConsumer.java](../../config/studio-consumer-smoke/src/main/java/consumer/PlainConsumer.java) et [SpringConsumer.java](../../config/studio-consumer-smoke/src/main/java/consumer/SpringConsumer.java). Ces consommateurs résolvent les JAR/POM produits, sans dépendances Gradle sur les projets sources. Le test Spring prouve l'intégration du SDK ; il ne qualifie pas un IdP réel.

Le paquet frontend livré peut être installé indépendamment :

```sh
npm install /chemin/gear4j/distributions/gear4j-studio-web-0.6.2.tgz
```

Voir son [README](../../gear4jtest-studio-web/README.md) pour les exports et l'injection du client. Le paquet n'impose pas de bundler et ne fournit pas de serveur d'authentification.

## Versionnement et limites assumées

Les modules Java suivent la version commune du build (`1.0.0-SNAPSHOT` ici), sans transformer les packages `@Experimental` en APIs stables. Le paquet npm suit sa version `0.6.2` et déclare le dist-tag `experimental` pour une publication éventuelle ; aucune publication ni attribution de `latest` n'a eu lieu. Les consommateurs doivent épingler leurs versions. HTTP reste en version 2, le schéma déclaratif et le profil de simulation en version 1.

Il n'existe pas de baseline publiée N-1 de Studio à comparer. Les contrats d'import/export, de protocole et les consommateurs vérifient le candidat actuel. Japicmp continue à protéger les APIs/SPI stables de Gear4J ; les artefacts entièrement expérimentaux sont exclus conformément à la [politique de compatibilité](../compatibility-policy.md), y compris lorsque le marqueur est importé dans `package-info.java`.

Limites acceptables pour une première release expérimentale : évolution documentée des APIs, migration limitée à la baseline P6 exacte, Chromium uniquement dans la qualification navigateur, SSE best-effort avec relecture du snapshot, simulation déclarative sans exécution métier. Le véritable dry-run du runtime et l'isolation avancée restent différés.

Une absence de preuve PostgreSQL ou d'analyse des vulnérabilités n'est pas une limite acceptée de release. Les signatures, namespace et droits de registre doivent également être qualifiés. Voir le [bilan final](verification-release-closure.md), le [gate PostgreSQL](postgresql-qualification.md) et la [matrice de sécurité](deployment-security.md).
