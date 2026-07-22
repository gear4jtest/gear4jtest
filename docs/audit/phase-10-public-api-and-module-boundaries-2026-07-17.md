# Phase 10 — Public API and module boundaries

**Date:** 17 July 2026
**Scope:** `core`, `external-api`, `jdbc`, `micrometer`, `spring`, Spring Boot starter and consumer smoke project.

## Objective

Eliminate the 13 baseline dependencies from public/SPI contracts to packages marked internal, remove residual JDBC code
from the provider-neutral module and establish a stable external error taxonomy without rewriting the runtime.

## Implemented changes

- Established `AssemblyLineExecutor` as the supported execution entry point and added
  `AssemblyLineExecutorBuilder` plus `AssemblyLineExecutors` for construction without internal imports.
- Added read-only `RunTrace` and `StationTrace` contracts; runtime mutable traces implement them internally and expose
  unmodifiable context/sub-operation/error collections.
- Promoted consumer-facing worker-concurrency configuration to `core.api.config` and made `FlowConfig` reject null policies at construction.
- Added `ExecutionContextLookup` and kept `ExecutionContextRegistry` as internal wiring.
- Added `RunPersistenceManager` and promoted persistence runtime monitoring records/interfaces to `core.persistence`.
- Migrated lifecycle SPIs, persistence records, JDBC, Micrometer and actuator integrations to the stable contracts.
- Replaced the Spring engine-builder customizer with `Gear4jAssemblyLineExecutorCustomizer` and exposed an
  `AssemblyLineExecutor` bean.
- Removed the unused JDBC input stream and duplicated metrics type from `external-api`.
- Added `Gear4jExternalException`, `ExternalErrorCode` and typed validation, not-found, conflict, storage and compilation
  failures; the former nested policy exception was removed before 1.0.
- Changed `ApiBoundarySourceTest` from a 13-entry baseline to an empty-set assertion and added the JDBC-import rule.

## Validation performed

- `javac --release 17` compilation of the production sources of `core`, `jdbc`, `micrometer`, `spring`,
  `spring-boot-starter`, `experimental-cache`, `external-api`, `external-jdbc`, `jackson` and `xml`.
- The unavailable third-party JDT, Spring Boot, Jackson and Micrometer types were represented by minimal local stubs;
  the actual project dependencies remain the source of truth for the Gradle build.
- Targeted compilation of every modified test covering the executor facade, flow-policy invariants, trace contracts, architecture boundaries,
  JDBC integration migrations, Spring integration, actuator integration and external error taxonomy.
- End-to-end standalone smoke tests:
  - `phase10-smoke=OK result=value-ok`;
  - `phase10-external-errors=OK`;
  - consumer-smoke source compilation against public contracts only.
- Source architecture scan: package marker violations = 0; core public/SPI internal dependencies = 0;
  provider-neutral JDBC imports = 0.
- Compiled-signature inspection of the new executor, trace, context-lookup and persistence-monitoring contracts found no
  dependency on `core.engine`, `core.execution` or `core.internal`.
- Documentation validation: 106 Markdown files with no broken local link and four valid YAML files.

Gradle could not be executed in the audit environment because the Gradle 9.6.1 distribution was not cached and
`services.gradle.org` was unreachable. The project build remains the final formatter, Testcontainers, coverage and
Japicmp gate.

## Migration notes

- Replace direct `AssemblyLineEngine` dependencies with `AssemblyLineExecutor` and
  `AssemblyLineExecutors.builder()`.
- Replace `AssemblyRunTrace`/`StationLogTrace` in consumer signatures with `RunTrace`/`StationTrace`.
- Replace `AssemblyRunManager` with `RunPersistenceManager`.
- Replace `Gear4jAssemblyLineEngineBuilderCustomizer` with `Gear4jAssemblyLineExecutorCustomizer`.
- Import persistence runtime monitoring from `io.github.gear4jtest.core.persistence`.

## Deferred

Compiler backend/classloader behavior and the Gradle plugin TestKit/configuration-cache work remain in phase 11.
Coverage ratchets, supply-chain enforcement and reproducibility remain in phase 12.
