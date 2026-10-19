# Contrats publics du candidat Studio

La revue conserve l’architecture P6. Aucun changement de signature Java de production ni de SQL n’est introduit par Release Readiness. Les modules Studio restent expérimentaux ; ne pas déduire une compatibilité universelle entre prototypes de leur présence dans Maven.

| Surface | Contrat de ce candidat | Règle de compatibilité |
| --- | --- | --- |
| Java management | `StudioManagementService` porte ACL, audit et cas d’usage ; le runtime n’est pas réexposé par la façade | Évolutions revues sur les consommateurs Java simple et Spring |
| SPI hôte | Identité, catalogue explicite, policy données, fixtures, publication et runtime sont des ports de confiance | L’hôte configure ces ports ; aucune requête navigateur ne les remplace |
| Identité | `principalId` stable et globalement scoppé ; `fromScopedSubject(authority, subject, …)` après authentification | Même mapping sur tous les targets ; les groupes/droits sont recalculés par la cible |
| Contexte | `RuntimeContext.id` stable, ancré dans la base et unique dans le parc configuré | Ni label `prod` partagé, ni redirection silencieuse de workspace ; pas de changement d’ancrage automatique |
| HTTP | Protocole `2`, attentes d’identité/contexte, DTO bornés, routes fédérables classées explicitement | Incompatibilité refusée ; pas de retry automatique d’un POST incertain |
| Définition | Modèle déclaratif `schemaVersion=1`, XML via adaptateur | Round-trip sémantique sur le sous-ensemble reconnu ; aucune conversion partielle |
| Draft | SOURCE conserve le texte exact autorisé, STRUCTURED une définition structurée | SOURCE reste non testable/publiable avant analyse et conversion explicites |
| Simulation | Profil `schemaVersion=1`, version du simulateur dans la provenance | Catalogue/build actuels, diagnostic de drift historique ; pas de reproduction du runtime passé |
| Persistance | Baseline SQL gelée et JSON interne associé | Pas de promesse d’import de JSON persistant arbitraire entre prototypes ; migration explicite pour les releases suivantes |
| Permissions | Lecture des simulations : `READ_SIMULATIONS` ; runs : `READ_RUNS` ; captures/observabilité selon les droits dédiés | Capabilities runtime et permissions acteur restent deux contrôles distincts |
| Temps réel | Snapshot durable et notifications SSE best effort ; recharger après reconnexion | Aucun contrat de journal événementiel exhaustif ni replay durable |
| npm | `@gear4j/studio-web@0.6.2`, exports explicités dans `package.json` | Composants utilisés par les applications livrées ; injection du transport/auth par l’hôte |

La version immuable conserve son artefact exact, hash, modèle/adaptateur, contexte, build et catalogue. Ce sont des preuves de provenance, pas une garantie qu’un autre environnement reconstruira le comportement historique.

La revue de distribution vérifie aussi les dépendances transitives depuis un dépôt Maven local isolé et le paquet npm installé dans un consommateur temporaire. Les lanceurs Java/Spring de démonstration sont distribuables pour qualification locale mais leurs publications Maven restent désactivées. Le standalone à tokens fixes est un lanceur local, pas une intégration OIDC entreprise livrée clé en main.

Les changements frontend de cette phase corrigent l’appel au `fetch` natif dans le navigateur, le pattern HTML des clés, les débordements des formulaires sur petit écran et l’effacement des données affichées lors d’un refus de relecture après révocation. Les transports injectés restent supportés. Une navigation vers une autre ressource inexistante préserve le travail local de la sélection courante.

La finalisation conserve les signatures Java de production et les octets des migrations SQL. Elle corrige la qualification de distribution : métadonnées POM explicitement expérimentales, marqueurs de stabilité importés reconnus, LICENSE/NOTICE et dist-tag npm experimental. Il n'existe pas de baseline Studio N-1 publiée ; les consommateurs vérifient les artefacts du candidat actuel.
