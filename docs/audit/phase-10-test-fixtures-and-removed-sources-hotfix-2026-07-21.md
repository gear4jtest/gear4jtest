# Phase 10 hotfix — engine-owned test contexts and removed sources

**Date:** 21 July 2026

## Symptoms

Two regressions were reported after applying the phase 10 ZIP over an existing working tree:

1. `ApiBoundarySourceTest.providerNeutralExternalApi_shouldNotImportJdbcPackages` still found
   `gear4jtest-external-api/.../JdbcArtifactInputStream.java`.
2. `AssemblyLineCallStationStrategyStatusMappingTest` supplied a custom public
   `StationExecutionContext` to engine code that now requires an engine-owned context.

## Root causes

### Removed files remained after overlay extraction

The phase 10 archive no longer contains the obsolete JDBC source files, but extracting a ZIP over an existing repository
cannot delete files that were removed from the archive. The following phase 9 files can therefore remain in a working tree:

- `gear4jtest-external-api/src/main/java/io/github/gear4jtest/external/api/artifact/JdbcArtifactInputStream.java`
- `gear4jtest-external-api/src/main/java/io/github/gear4jtest/external/api/artifact/ArtifactStoreMetrics.java`

They must be deleted explicitly. The accompanying Git patch records both deletions.

### Test fixture no longer represented a runtime-owned context

Phase 10 intentionally made engine-only mutable trace access reject arbitrary public `StationExecutionContext`
implementations. The status-mapping test still used a hand-written public context, so it failed before reaching the behavior
under test.

The fixture now uses `DefaultStationExecutionContext`, the actual engine-owned implementation, with an
`ExecutionSupport` instance and the expected mutable station trace.

## Production impact

No production behavior or public API changed in this hotfix. The engine ownership check remains strict.

## Validation

- all `gear4jtest-core` production sources compiled with `javac --release 17` and minimal SLF4J stubs;
- `AssemblyLineCallStationStrategyStatusMappingTest` compiled with Java 17 and minimal JUnit/AssertJ stubs;
- no JDBC imports remain under `gear4jtest-external-api/src/main/java` in the clean tree;
- both obsolete provider-specific source files are absent from the clean tree.

Gradle could not be executed in the delivery environment because `services.gradle.org` could not be resolved.
