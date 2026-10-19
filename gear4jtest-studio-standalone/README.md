# Gear4J Studio standalone — expérimental

Ce module fournit les contrats de fédération et un **lanceur de démonstration local**. Ce lanceur à tokens configurés n'est pas un backoffice de production avec OIDC intégré. Les APIs restent expérimentales.

```sh
./gradlew :gear4jtest-studio-standalone:installDist
GEAR4J_STUDIO_TOKEN='<token local de test>' \
  gear4jtest-studio-standalone/build/install/gear4jtest-studio-standalone/bin/gear4jtest-studio-standalone \
  gear4jtest-studio-standalone/example.properties
```

Démarrer les cibles Java/Spring séparément. L'exemple utilise HTTP loopback et des variables d'environnement de test. Ne pas réutiliser ce profil sur Internet. Les credentials distants sont référencés par nom de variable, jamais sauvegardés dans une chaîne ni envoyés au navigateur. En déploiement réel, prévoir des credentials dédiés par target, la vérification d'identité scoppée, les ACL et la configuration TLS/proxy de l'hôte.

Voir `docs/studio/release-maturity.md` et `docs/studio/deployment-security.md` dans l'archive source pour les responsabilités de l'hôte et la qualification restante. Une simulation déclarative n'exécute aucun code métier ; elle ne garantit pas l'isolation d'un controlled test réel.
