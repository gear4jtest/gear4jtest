# 0053 — Studio fédère des contextes management autonomes

Statut : accepté pour les API expérimentales Studio P5, 2026-10-07.

Une application autonome doit piloter plusieurs applications sans déplacer les
contrôles ACL, les politiques de données et le cycle TEST/RUN hors de leurs hôtes.
Dupliquer les versions et piloter un déploiement distribué n’est pas nécessaire
à ce parcours. L’annuaire Application/Target est donc une configuration immutable
et bornée. Les ressources demeurent ancrées dans leur RuntimeContext persistant.

Le transport HTTP est extrait de la démo. Un adaptateur distant vérifie protocole,
contexte et subject authentifié, puis la cible contrôle les préconditions de
liaison sur la requête effective. La visibilité d’une cible ne donne aucun droit
sur ses ressources. Les credentials proviennent de l’hôte, par utilisateur et
cible, sans service account élevé ni identité confiée au navigateur.

Les mutations ne sont pas automatiquement répétées ; leur réponse peut être
inconnue après coupure. Les reçus et le refresh de publication restent les
mécanismes existants de reprise. Les réponses et la concurrence sont bornées,
les redirects refusés. Le standalone utilise le polling des snapshots durables,
sans journal d’événements ou orchestration supplémentaire. Son sélecteur et son
workspace sont les composants publics distribués.

La clôture P4.1 sépare admission du texte synthétique et résolution de référence.
L’input synthétique persisté est exécuté exactement. L’input externe est lié à un
HMAC de contenu calculé par une fabrique ; seule la provenance est conservée.
Cette attestation permet de comparer les inputs sous une même clé mais ne
remplace pas l’immuabilité ni la disponibilité du provider.

Conséquences : le stockage embarqué ne dépend pas du standalone ; un hôte partagé
doit fournir son transport sécurisé et son intégration d’identité. Les contextes
ne sont pas automatiquement fusionnés. Activation/DeploymentBinding et contrôle
desired/observed restent différés ; P6 concerne la simulation. Voir le
[guide P5](../studio/p5.md) pour les limites, contrats et instructions de lancement.
