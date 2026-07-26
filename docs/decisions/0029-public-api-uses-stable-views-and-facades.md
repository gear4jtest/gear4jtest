# ADR 0029 — Public API uses stable views and facades

## Status

Accepted — 17 July 2026.

## Context

The effective consumer API exposed mutable runtime traces, execution registries, concurrency configuration and
persistence-monitoring types from packages marked `@Internal`. Japicmp excluded those packages, so a consumer could be
broken while the compatibility gate remained green. The provider-neutral external API also retained unused JDBC code.

## Decision

- Applications execute pipelines through `AssemblyLineExecutor`, created by `AssemblyLineExecutors` or injected by
  Spring. `AssemblyLineEngine` remains the internal default implementation.
- `RunTrace` and `StationTrace` are read-only contracts. Mutable trace implementations remain under
  `core.execution.trace`; collection/map views exposed by the public contracts are unmodifiable.
- Worker concurrency configuration lives in `core.api.config`.
- Context lookup is exposed through `ExecutionContextLookup`; the registry implementation stays internal.
- Persistence lifecycle and operational monitoring live in `core.persistence` through `RunPersistenceManager`,
  `PersistenceRuntimeMonitor`, `PersistenceRuntimeStats` and `PersistenceOperationalStatus`.
- Spring customization uses `Gear4jAssemblyLineExecutorCustomizer`, which exposes only stable options.
- External API failures use `Gear4jExternalException` and `ExternalErrorCode`.
- Architecture tests require zero exported API/SPI signature dependencies on packages or types marked `@Internal`
  across all published modules, and forbid JDBC imports in `gear4jtest-external-api`.

## Consequences

Consumer code no longer needs internal imports for normal execution, traces, persistence or Spring customization.
Advanced code that depended directly on engine internals must migrate before 1.0. Internal implementations remain free
to evolve while the public views and facades become the compatibility surface checked by Japicmp.
