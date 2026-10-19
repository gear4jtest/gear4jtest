# Sécurité de déploiement — contrat et qualification

Les lanceurs fournis servent une démonstration locale sur loopback avec tokens issus de l’environnement. Ils ne doivent pas être exposés sur Internet comme un backoffice d’entreprise. Un déploiement réel fournit un hôte de management sécurisé et ses adaptateurs ; Studio ne remplace pas l’IdP ni la politique réseau de cet hôte.

| Condition | Vérification dans cette phase | Limite à qualifier sur le déploiement retenu |
| --- | --- | --- |
| Authentification | Applications packagées : absence/mauvais token refusés ; token de target distinct de celui du standalone | Validation OIDC réelle : signature, issuer, audience, expiration, rotation de clés ; aucun IdP d’entreprise fourni ici |
| Identité fédérée | Même subject sous deux authorities refusé au handshake ; mapping canonique conservé | Accord sur les identités pairwise et la politique de groupes entre applications |
| Autorisations | ACL côté serveur ; création interdite au viewer ; ressource cachée ; révocation efface l’éditeur après relecture refusée | Politique métiers, droits administrateur, séparation opérateur/éditeur à configurer et tester par l’hôte |
| Targets | Inventaire filtré sans endpoint/credentials ; cible non autorisée invisible ; contexte attendu vérifié | Configuration stable et unique des contextes, DNS/réseau et allowlist des endpoints |
| TLS | `DeploymentSecurityTest` exécute un vrai serveur HTTPS exigeant un certificat client : réussite avec confiance mutuelle, rejet sans certificat, avec hostname faux ou truststore incorrect | Certificats/CA de l’organisation, expiration/rotation, reverse proxy et configuration mTLS de production |
| Credentials | Rotation du bearer vérifiée ; navigateur et target utilisent des tokens différents ; absence dans inventaire, stockage navigateur et logs de qualification | Coffre de secrets, renouvellement et timeout du provider applicatif à qualifier |
| HTTP | Refus Origin non autorisé, no-store/nosniff, routes allowlistées ; tests existants des limites, redirects et capacité par target | En-têtes de proxy, taille/temps des requêtes, TLS entrant, cookies/session/CSRF selon l’hôte |

`DeploymentSecurityTest` utilise des certificats et tokens synthétiques créés pour le test ; aucun trust manager permissif, aucune désactivation du contrôle hostname. Il vérifie le vrai client HTTP injecté et la vraie façade HTTP. Ce test ne vaut pas validation d’un IdP externe ou d’un proxy corporate.

Le handler management indépendant du framework suppose une authentification déjà établie par son hôte. Ne jamais construire `StudioIdentity` depuis un header de subject/groupes envoyé librement par le navigateur. Avec Spring, appliquer la chaîne de sécurité de l’application ; hors Spring, fournir le même contrat d’identité authentifiée. En OIDC, un `sub` brut n’est pas globalement unique. `fromScopedSubject` ne valide pas le token : il scope une identité déjà authentifiée. Les ACL persistées utilisent le résultat stable de ce mapping.

Les credentials de chaque target sont obtenus côté serveur. Ne pas transférer automatiquement le token navigateur à toutes les applications. `TargetCredentials` est synchrone : tout I/O interne doit avoir une deadline courte, une capacité bornée et une politique d’échec explicite. Le timeout HTTP ne borne pas un provider qui bloque avant la requête. Le transport impose sa capacité globale et par target ; cela limite l’affamement sans promettre un circuit breaker distribué.

Le client HTTP injecté permet truststore, mTLS, proxy et instrumentation, sous les restrictions du transport (notamment pas de suivi automatique de redirection ni d’authentification/cookies implicites). Maintenir les vérifications hostname et CA. Conserver le handshake `/context` avant chaque opération : le coût de deux échanges est accepté ici pour éviter un cache de sécurité subtil. Les headers d’attentes restent revérifiés par la cible.

Une erreur de transport après une mutation n’établit pas que celle-ci n’a pas eu lieu : consulter l’état durable et les identités de tentative avant retry. Le proxy reste borné aux routes management classées ; ce n’est pas un proxy URL arbitraire.

Les policies de données s’appliquent avant persistance : secrets de configuration sous forme de références autorisées, captures autorisées/masquées/interdites, sources XML conservatrices, fixtures contrôlées. Le mode `synthetic` des démos ne doit jamais servir à une base métier. Une source XML invalide contenant des commentaires, CDATA, fragments inconnus ou valeurs non qualifiables peut être refusée : aucune libéralisation de cette policy n’a été introduite ici.

La révocation est réévaluée à chaque requête. Les octets déjà remis à un client ne peuvent pas être repris rétroactivement ; le correctif navigateur efface la sélection au refus de relecture, sans prétendre offrir une révocation push instantanée. Après perte d’accès fédéré, les composants isolent et abandonnent le workspace concerné.

Avant exposition réelle, exécuter cette matrice avec l’IdP, les certificats, le proxy, le coffre et les rôles retenus. Définir timeout, taille de requête, rate limiting, origines autorisées et journalisation expurgée au périmètre hôte. Sauvegardes et exports doivent recevoir au moins les mêmes protections que la base. Les résultats locaux ne remplacent pas cette qualification de configuration.
