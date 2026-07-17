# Phase 9 — Hotfix de compatibilité AssertJ

**Date :** 17 juillet 2026

## Problème

`AssemblyLineValidatorTest` utilisait `doesNotHaveMessageContaining(...)`, méthode absente de l'API `AbstractThrowableAssert` fournie par AssertJ 3.27.3.

## Correction

Les deux assertions utilisent désormais `hasMessageNotContaining(...)`, déjà employée dans plusieurs autres tests du projet avec la même version d'AssertJ.

Le comportement vérifié reste inchangé : les entrées XML trop volumineuses doivent être rejetées par la limite de taille avant d'atteindre le message générique de validation XSD.

## Fichier modifié

- `gear4jtest-xml/src/test/java/io/github/gear4jtest/xml/validator/AssemblyLineValidatorTest.java`

## Validation

- aucune occurrence de `doesNotHaveMessageContaining` ne reste dans le dépôt ;
- `hasMessageNotContaining` est déjà utilisé par les tests existants du projet ;
- aucun code de production n'est modifié.
