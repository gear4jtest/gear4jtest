# gear4jtest-studio-spring-demo

Hôte Spring utilisant le même service de management, stockage, ACL et composants.

Module expérimental. Voir le [guide P4](../docs/studio/p4.md) et le [bilan de vérification](../docs/studio/verification-p4.md). P4 ajoute le journal durable des runs, leur inspection sécurisée, les indicateurs et les snapshots temps réel au cycle de vie P3.

Cette distribution est une démonstration locale de qualification, sans publication Maven.
Elle ne livre pas une intégration OIDC/Spring Security de production prête à l'emploi.
L'hôte doit fournir et qualifier son profil d'authentification et de sécurité.
Voir la [maturité du candidat](../docs/studio/release-maturity.md).
