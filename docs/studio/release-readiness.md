# Gear4J Studio — proposition de stabilisation avant release

Statut : **proposé, non démarré**. P6 est close ; aucun nouvel axe fonctionnel
n’est lancé automatiquement. Objectif : un candidat de release exploitable,
installable et vérifié, avec des limites publiées. Durée cible : une itération
courte, à ajuster après choix de la matrice de déploiement supportée.

| Lot | Travail borné | Critère de sortie |
| --- | --- | --- |
| 1. Contrats et baseline | Inventorier Java API/SPI, HTTP protocole 2, JSON persisté, ACL, événements et exports du composant. Fixer versions, stabilité et règles de compatibilité, notamment principalId, READ_SIMULATIONS et diagnostics. Définir les bases réellement concernées par un upgrade. | Diff d’API revu, formats versionnés et matrice de compatibilité documentée ; décision explicite sur les données de prototypes P1–P6. |
| 2. Migrations réelles | Geler la baseline SQL de release ; écrire des migrations incrémentales pour les versions dont l’upgrade est déclaré supporté. Tester sauvegarde/restauration, checksums, reprise après échec et concurrence au démarrage, avec scénarios, ACL, sources invalides, versions, runs et simulations déjà présents. | Upgrade de bases peuplées et restauration exécutés sans perte ni ré-ancrage silencieux du RuntimeContext ; chaque chemin non supporté est refusé/documenté. |
| 3. PostgreSQL exécuté | Exécuter réellement la suite PostgreSQL Studio et les chemins JDBC dont elle dépend, sur la version retenue, via Testcontainers/CI ou un service dédié. Couvrir transactions, contention, idempotence, pages filtrées, rétention et snapshots. | Job obligatoire non ignoré, preuves d’exécution conservées, aucun remplacement par H2. Résoudre aussi l’échec historique de couverture MigrationLockStore sans baisser son seuil. |
| 4. Navigateur | Parcours Playwright réels sur Java, Spring et standalone : reprise SOURCE invalide, authoring, scénario, test contrôlé, TEST/RUN, simulation, avertissement historique, reconnexion, refus/révocation ACL et changement de target. Revue visuelle ciblée clavier/focus/petits écrans. | Parcours critiques verts dans la matrice de navigateurs déclarée ; aucune perte de saisie ni fuite de données lors d’un changement d’accès/cible. jsdom reste complémentaire. |
| 5. Distribution et adoption | Produire JAR/POM/sources/Javadoc, distributions de lancement et paquet npm. Installer les artefacts dans deux consommateurs propres, Spring et Java simple, et intégrer les composants depuis le paquet distribué. Vérifier configuration, arrêt propre, licences, provenance et procédures de publication. | Installation depuis artefacts seuls, démarrage/reprise et intégration publique réussis ; répétition de publication en staging/dry-run, puis décision distincte de publication. |
| 6. Documentation et revue finale | Mettre à jour guides installation, authentification/ACL, données sensibles, migrations, supervision/rétention, intégration et dépannage. Expliquer validation/test contrôlé/simulation/dry-run différé. Exécuter le build global et archiver les preuves. | Candidat traçable, build et gates verts, limitations connues explicites, checklist de release revue. |

Ordre conseillé : fixer les contrats et chemins d’upgrade avant les migrations ;
qualifier PostgreSQL pendant ce travail ; exercer le navigateur sur le candidat
assemblé ; valider la consommation des artefacts avant le go/no-go final.
Aucune publication publique ni migration de base utilisateur n’est réalisée par
la clôture P6. Les futurs déploiements/convergence, objets simulés riches et
l’isolation de code métier restent des choix produit séparés.

Le choix d’avoir modifié V1 pendant les phases expérimentales n’oblige pas à
inventer un upgrade universel depuis chaque prototype. Il oblige à identifier
les bases à préserver, retenir une baseline et annoncer exactement les chemins
qualifiés avant de promettre une migration réelle.
