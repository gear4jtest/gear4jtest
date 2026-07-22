# Phase 10 hotfix — exception taxonomy test alignment

**Date:** 22 July 2026

## Problem

`ArtifactConsistencyChecker` now raises the public, categorized
`OperationChainNotFoundException` when the operation-chain configuration is
missing. Its test still expected the former generic `NoSuchElementException`,
causing the suite to fail even though the production behavior matches the new
external error taxonomy.

## Correction

`ArtifactConsistencyCheckerTest` now expects
`OperationChainNotFoundException` and continues to verify that the missing
assembly-line identifier is present in the message.

No production code, public API, schema, or coverage threshold was changed.

## Verification

A repository-wide search found no remaining Java test expectation for
`NoSuchElementException` in this scenario.

Recommended commands:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*ArtifactConsistencyCheckerTest'
./gradlew check
```
