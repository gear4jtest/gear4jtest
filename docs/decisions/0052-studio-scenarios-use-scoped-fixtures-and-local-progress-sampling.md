# 0052 — Scénarios à portée explicite et échantillonnage local

Statut : accepté pour les API expérimentales Studio P4.1, 2026-10-05.

P4 écrivait des snapshots JDBC depuis les callbacks de stations et regroupait
les métriques par nodeId. Les runs sans résultat après crash restaient actifs.
Les scénarios réutilisables avaient été explicitement déplacés en P4.1.

Les callbacks alimentent maintenant un état borné ; un dispatcher local partagé
échantillonne et persiste hors du verrou de l’observer. La fin métier produit une
écriture finale explicite. Les mises à jour verrouillent uniquement la ligne du
run ; admission/audit conservent les transactions management existantes.

staleAt décrit la fraîcheur indépendamment du résultat. Marquage et purge après
grâce sont des opérations hôte bornées, non planifiées par Studio. Aucun résultat
métier ni reprise n’est inféré d’un silence. Les stations sont agrégées par
version/mode/hash/catalogue/build, ou séparées par run si non attestées.

Les scénarios héritent d’un scope chaîne/draft avec permissions dédiées, et leurs
révisions sont immuables. La cible du test est exacte. Une fixture de draft ne
peut alimenter un run de version partagé. L’hôte admet explicitement fixtures et
attentes ; le verdict est calculé avant redaction sans copie des valeurs dans
l’audit ou le verdict. Aucune politique implicite d’acceptation du texte libre.

Les observations intermédiaires peuvent être perdues/coalescées. La persistance
finale peut attendre une écriture du même run, après retour du métier. Le
constructeur d’observer exige un dispatcher explicitement détenu par l’hôte.
La reproductibilité d’une fixture référencée dépend de l’immuabilité garantie
par son fournisseur. Les scénarios restent String → String avec assertions
simples, sans suite, mock, simulation ni infrastructure multi-target.
Voir [le guide P4.1](../studio/p41.md) pour les contrats détaillés.
