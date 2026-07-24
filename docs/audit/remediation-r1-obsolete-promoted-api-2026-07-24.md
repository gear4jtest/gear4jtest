# Remediation R1 — Obsolete promoted API cleanup

**Date:** 24 July 2026
**Finding:** F-02, with the related documentation correction from F-23

## Scope

Phase R1 removes types left behind after persistence-monitoring and worker-concurrency contracts were promoted to their
stable packages. Gear4J is still pre-1.0 and has no production consumers, so the temporary migration aliases are removed
instead of carrying two competing API surfaces into 1.0.

## Implemented changes

- Removed the obsolete persistence-monitoring copies from `io.github.gear4jtest.core.execution`:
  - `PersistenceOperationalStatus`;
  - `PersistenceRuntimeMonitor`;
  - `PersistenceRuntimeStats`.
- Removed the temporary `AssemblyRunManager` alias. Consumers use
  `io.github.gear4jtest.core.persistence.RunPersistenceManager`.
- Removed the obsolete worker-concurrency copies from `io.github.gear4jtest.core.engine.support`:
  - `WorkerConcurrencyConfiguration`;
  - `WorkerConcurrencyPolicy`;
  - `WorkerConcurrencyRegistryConfiguration`;
  - `WorkerLockAcquisitionPolicy`.
- Removed the unused `WorkerConcurrencyStrategy`.
- Added `verifyCoreArtifactApiSurface`. The task inspects both the core binary JAR and sources JAR, and fails if any
  obsolete type is reintroduced. It is part of the root `check` lifecycle.
- Updated the JDBC README, the persistence-batching ADR and the previous audit status so they reference the stable
  contracts and no longer claim unqualified closure.

## Supported replacements

| Removed package | Stable package |
|---|---|
| `core.execution.PersistenceRuntimeMonitor` | `core.persistence.PersistenceRuntimeMonitor` |
| `core.execution.PersistenceOperationalStatus` | `core.persistence.PersistenceOperationalStatus` |
| `core.execution.PersistenceRuntimeStats` | `core.persistence.PersistenceRuntimeStats` |
| `core.execution.AssemblyRunManager` | `core.persistence.RunPersistenceManager` |
| `core.engine.support.WorkerConcurrencyConfiguration` | `core.api.config.WorkerConcurrencyConfiguration` |
| `core.engine.support.WorkerConcurrencyPolicy` | `core.api.config.WorkerConcurrencyPolicy` |
| `core.engine.support.WorkerConcurrencyRegistryConfiguration` | `core.api.config.WorkerConcurrencyRegistryConfiguration` |
| `core.engine.support.WorkerLockAcquisitionPolicy` | `core.api.config.WorkerLockAcquisitionPolicy` |

`WorkerConcurrencyStrategy` has no replacement because it was unused. The supported behavior is expressed by
`WorkerConcurrencyPolicy` together with `WorkerLockAcquisitionPolicy`.

## Validation

- A controlled source archive made from the initial R1 tree contained all nine obsolete types.
- The same scan after the correction reports no obsolete entry.
- A production-source import scan reports no compiled reference to a removed type.
- All stable replacement source files remain present.
- No JavaScript package-manager lockfile exists in the project, so this phase cannot alter registry URLs.

The following project validation was attempted:

```bash
./gradlew --no-daemon :gear4jtest-core:test verifyCoreArtifactApiSurface
```

It could not start because Gradle 9.6.1 is not cached in the audit environment and access to
`services.gradle.org` is unavailable. A connected development or CI host must therefore run:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test verifyCoreArtifactApiSurface
./gradlew check
```

R1 is implemented in source; its dynamic qualification remains pending until those commands pass.
