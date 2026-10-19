# Clôture P6 — revue critique du 8 octobre 2026

Base : archive P6 utilisateur, SHA-256
`67fb308e0725ed251b903b484eb9beb76531aaab34ad2add4862618caaea3451`.

| Remarque | Décision | Changement retenu |
| --- | --- | --- |
| Conformité avec Gear4J | D’accord : deux chemins sémantiques peuvent diverger même en partageant GEL. | Suite différentielle côté tests, avec compilation XML et vrai moteur. Aucune délégation au moteur en production. |
| Version historique et contexte actuel | D’accord : la provenance brute n’est pas un avertissement suffisamment visible. | WARNING localisés dans le plan et persistés dans le rapport ; affichage à la relecture. Aucun ancien runtime reconstruit. |
| READ_RUNS pour lire une simulation | D’accord sur l’incohérence ; élargir/renommer READ_RUNS ferait évoluer aussi les consommateurs P4. | READ_SIMULATIONS dédiée, indépendante de READ_RUNS et de SIMULATE. Les règles de capture restent inchangées. |
| Documentation dry-run | D’accord : des formulations de roadmap regroupaient deux produits distincts. | DELIVERED pour la simulation déclarative P6 ; DEFERRED pour le dry-run du vrai moteur et l’isolation avancée. |

## Conformité différentielle

`DeclarativeSimulatorConformanceTest` donne la même `ChainDefinition` et la même
entrée exacte à deux chemins : l’interpréteur avec ses substitutions et
`LocalGear4jRuntime.execute` avec des implémentations pures équivalentes.
Ce deuxième chemin utilise l’adaptateur XML, le générateur, javac, l’injection
et l’executor Gear4J réels. Il n’exécute pas un second modèle simulé dans le test.
Un wrapper de test observe les stations sans décider des branches ni modifier
leurs entrées, sorties ou résultats. Les exceptions sont observées puis propagées
inchangées vers la frontière d’erreur normale du moteur.

Les 19 cas couvrent la propagation séquentielle, l’identité de textes vides/espacés/
Unicode, une substitution de sortie, un intermédiaire typé String → Integer →
String, les deux branches, plusieurs choix successifs, les conditions appliquées
à la sortie précédente, le court-circuit GEL, l’erreur d’évaluation de condition,
l’échec en début/milieu de chaîne et dans chacune des branches. Une branche non
sélectionnée contenant un opérateur qui échouerait ne doit jamais être exécutée.

Les assertions comparent l’ordre réel des opérations, chaque entrée/sortie,
la branche sélectionnée, l’issue globale et le point d’échec ; les nœuds marqués
SKIPPED/NOT_EXECUTED ne doivent pas apparaître parmi les appels réels. Des attentes
explicites évitent qu’un même résultat faux sur les deux chemins suffise à passer.
Seuls les libellés SUCCEEDED/SUBSTITUTED sont rapprochés ; les identifiants de
trace, horodatages et messages d’exception ne sont pas des invariants communs.
La simulation ne capture pas de sortie en échec.

Ce corpus est un garde-fou de régression du sous-ensemble actuel, pas une preuve
sur toutes les entrées ou constructions Gear4J. Chaque extension du modèle ou
des règles de contrôle de flux devra ajouter ses cas aux deux chemins. Les tests
unitaires d’admission, de bornes scalaires et d’absence d’appel métier sont conservés.
Pas de couverture revendiquée des containers parallèles, délais, annulation,
fallbacks ou appels imbriqués absents du modèle Studio actuel.

## Provenance et droits

`HISTORICAL_CONTEXT_DIFFERS` est un avertissement, jamais une approbation de la
version ni un substitut aux erreurs de contrat. Le fingerprint inclut les
contrats et bindings ; un écart de catalogue peut concerner une opération non
utilisée, donc le diagnostic reste volontairement conservateur. Un build changé
ne prouve pas non plus un changement du comportement. Le rapport indique que le
contexte diffère sans prétendre expliquer un écart métier.

Les diagnostics sont établis lors du calcul et figés avec le rapport, pas
recalculés à la consultation. Les rapports anciens sans liste de diagnostics
restent lisibles ; une liste vide ne certifie jamais une reproduction historique.
La révision de définition et le hash de la Version restent exacts et inchangés.

| Action | Permissions supplémentaires à READ |
| --- | --- |
| Préparer/simuler un scénario | READ_SCENARIOS sur son scope ; SIMULATE sur la cible |
| Consulter un rapport / le retrouver dans l’historique | READ_SCENARIOS sur son scope ; READ_SIMULATIONS sur la cible |
| Consulter les captures du rapport | READ_OBSERVABILITY sur la cible |
| Consulter de vrais runs | READ_RUNS, sans droit implicite sur les simulations |

Le POST SIMULATE retourne son résultat et autorise la relecture idempotente de
cette même commande par le même acteur toujours autorisé. Il ne donne pas accès
au GET d’autres rapports ni à leur historique. Les rapports des drafts privés
restent filtrés. Les ACL ne reçoivent aucun droit nouveau implicitement, hors
administrateurs/owners dont le contrat est déjà l’administration complète.
Aucun changement SQL n’est nécessaire dans ce correctif : les diagnostics sont
ajoutés au snapshot JSON existant. Le constructeur du record public expérimental
`StudioSimulation` reçoit désormais cette liste ; les intégrations qui le
construisent directement doivent être adaptées.

## Fin de phase

P6 est close sur ce périmètre. La qualification de release est une étape distincte :
[stabilisation et qualification](release-readiness.md). Le dry-run du moteur reste
[différé](../roadmap/dry-run-and-mock-configuration.md). Les résultats exécutés et
limites de vérification de ce correctif figurent dans
[verification-p6-closure.md](verification-p6-closure.md).
