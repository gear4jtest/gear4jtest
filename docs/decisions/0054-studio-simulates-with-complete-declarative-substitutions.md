# 0054 — Simulation par substitution complète et contrats fédérés explicites

Statut : accepté pour les API expérimentales P6, 2026-10-07.

Un test contrôlé appelle les opérations et peut avoir des effets externes. P6
ajoute une expérience distincte : l’interpréteur du modèle déclaratif ne reçoit
aucun moyen d’appeler ces opérations. Tous les nœuds, y compris les branches non
sélectionnées, ont une substitution explicite versionnée avec le scénario.
Les conditions utilisent GEL ; les mocks sont des données scalaires bornées.
Les contrats non supportés produisent un diagnostic, jamais une approximation.

L’admission des mocks est un opt-in de la policy hôte. La permission SIMULATE
est indépendante de TEST. READ_SIMULATIONS est indépendante de READ_RUNS ;
READ_OBSERVABILITY reste nécessaire pour consulter les captures. Les rapports durables et leur rétention sont distincts
des runs et métriques métier. Un résultat simulé n’autorise pas une publication.
Le rapport pointe vers les révisions exactes de définition et de scénario,
ainsi que le moteur, contexte, build et catalogue ayant produit ce résultat.

La revue finale P5 explicite l’identité canonique principalId et fait évoluer le
protocole HTTP à 2, sans fallback ambigu. Le contexte persistant garde une identité
globale, différente d’un libellé d’environnement ou d’une instance de JVM. La
capacité distante est bornée globalement et par cible ; un HttpClient sécurisé
peut être injecté sans céder la propriété de ses executors. Un contrat de routes
unique est partagé entre dispatch HTTP et fédération. Le handshake systématique
est conservé jusqu’à justification mesurée d’un cache.

Cette décision complète [ADR 0053](0053-studio-federates-authoritative-management-contexts.md).
Le déploiement desired/observed et l’isolation de code Java arbitraire restent
hors du lot. Voir [le guide P6](../studio/p6.md) pour les décisions critiques,
limites, changements d’API, sécurité des données et modalités d’upgrade.

Revue de clôture P6 : une suite différentielle uniquement côté tests confronte
le simulateur au chemin XML → compilation → moteur Gear4J avec des opérateurs
purs. La production ne reçoit aucune dépendance d’exécution supplémentaire.
Les écarts de provenance historique produisent des avertissements persistés.
La simulation déclarative est DELIVERED ; le dry-run du moteur et l’isolation
avancée restent DEFERRED. Voir [la clôture P6](../studio/p6-closure.md).
