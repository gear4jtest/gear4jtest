# gear4jtest-studio-demo

Hôte Java sans Spring, stockage H2 durable, HTTP local et composants web intégrables.

Module expérimental. Voir le [guide P5](../docs/studio/p5.md) et le [bilan de vérification](../docs/studio/verification-p5.md). La démo Java reste embarquée et peut aussi être déclarée comme cible du Studio autonome. Les routes réutilisables et `RequestActorContext` se trouvent désormais dans `gear4jtest-studio-http`.

Cette distribution est une démonstration locale de qualification, sans publication Maven.
Elle ne livre pas une intégration OIDC/Spring Security de production prête à l'emploi.
L'hôte doit fournir et qualifier son profil d'authentification et de sécurité.
Voir la [maturité du candidat](../docs/studio/release-maturity.md).
