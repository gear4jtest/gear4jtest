# 7 September audit — phase 1 resource lifecycle

Status: **Implemented; full Gradle qualification pending**.

Baseline: gear4jtest-20260906-213514.zip, SHA-256
699e8f346539217117ec4343161537b130534f4bb7c54142bcf5589abf169a65.

This delivery implements phase 1 of the [remediation plan](../roadmap/audit-remediation-2026-09-07.md).
Finding identifiers refer to audit-technique-gear4j-2026-09-07.pdf, not the older
audit already present in this repository. No phase 2–4 change is claimed here.

## Changes

| Finding | Changed code | Result |
| --- | --- | --- |
| F01, P1 | external-api/AssemblyLineStoreResolver.acquire, release and close | Each installed entry returns its own provider acquisition, including shared-identity stores and same-identity replacements. |
| F02, P1 | external-api/AssemblyLineStoreResolver.StoreLease | Cache ownership and operation borrows are counted separately. Evicted, replaced or invalidated entries remain leased while in use. |
| F02, P1 | external-api/AssemblyLinePublicationService.registerAssemblyLine and promoteTestToRun | The lease encloses the publication steps that use the selected store, through commit or exceptional exit. |
| F02, P1 | external-api/GeneratedAssemblyLineLoader.readArtifact | The lease encloses metadata validation, byte reading and input-stream closure. The stream closes before the lease. |
| F03, P1 | core/ParallelContainerBranchExecutor.execute | Unexpected RuntimeException/Error paths attempt cancellation of every outstanding submitted future and rethrow the initial failure. Cleanup failures are suppressed. |

ArtifactStoreResolutionStats documents the revised occupancy semantics. ADR 0043,
the external loading architecture and runtime guarantees describe the implemented
ownership and cancellation behavior.

No public method signature, dependency version, schema, project name or executor
ownership policy changes. StoreLease is an internal API. Application-owned
providers with the default no-op release still retain their stores.

## Regression tests added

Seventeen new JUnit tests were added; three existing resolver tests were adapted
to the borrow API and balanced provider-release contract.

| Test class | New cases |
| --- | --- |
| ParallelContainerBranchExecutorTest | 3: later predicate failure, fatal branch Error, cancellation failure while cancelling multiple siblings. Checks preserve cause identity, interruption and caller executor ownership. |
| AssemblyLineStoreLeaseTest | 9: real provider sharing, cross-thread eviction during stream use, final borrower after invalidation, active borrow after shutdown, replacement, same-instance replacement, operation plus cleanup failure, multiple close failures, caller-owned store. |
| AssemblyLineStoreLeaseConsumerTest | 5: publication success, staging failure, promotion, successful artifact loading and failed artifact reading while the store is evicted. |

Concurrency checks use coordination latches or futures with five-second bounds,
not sleeps. Cancelling a future is distinguished from observing the cooperative
worker exit. The pre-existing concurrent-initialization test still verifies one
acquisition for sixteen concurrent requests.

## Validation actually performed

JDK: OpenJDK 17.0.20. No Gradle distribution or project dependency cache was
available in the execution environment.

| Check | Observed result | Scope and limits |
| --- | --- | --- |
| JDK 17 compilation | Pass | All 283 core production sources, then external lifecycle sources and their source dependencies, including every changed production class. Minimal stand-ins supplied only SLF4J and JSpecify APIs. This is not compilation against the resolved production dependency graph. |
| Local executable probes | 15 scenarios passed | Three parallel-failure scenarios and twelve store/consumer scenarios. These are separate JDK harnesses exercising production code, not executions of the JUnit suite. Real DefaultArtifactStoreProvider and Java compiler were used for the applicable paths. |
| Focused Gradle tests | Blocked, exit 1 | Wrapper download failed before Gradle build startup. JUnit sources were not compiled or run by Gradle. |
| spotlessApply | Blocked, exit 1 | Same distribution download failure. Formatter compliance is unverified. |
| check | Blocked, exit 1 | Same distribution download failure. Checkstyle, other module tests and aggregate gates are unverified. |
| git diff --check | Pass | Whitespace/error check only; does not replace Spotless or Checkstyle. |

The wrapper attempted https://services.gradle.org/distributions/gradle-9.6.1-bin.zip
and reported: Network is unreachable. No build rule, dependency version or gate
was weakened to work around this limitation.

Machine-readable command outcomes and local probe transcripts are retained in
[phase 1 validation results](evidence/remediation-2026-09-07-phase-1-validation.json).
Local stand-ins and exploratory probe classes are not production sources and
are not installed as project dependencies.

Commands to run in the normal development/CI environment:

    bash gradlew --no-daemon :gear4jtest-core:test --tests '*ParallelContainerBranchExecutorTest' :gear4jtest-external-api:test --tests '*AssemblyLineStore*Test' --tests '*GeneratedAssemblyLineLoaderTest'
    bash gradlew --no-daemon spotlessApply
    bash gradlew --no-daemon check

If formatting changes the touched files, include those changes in the phase 1
commit and rerun the focused tests and check task before integration.

## Limits and remaining work

- F03 requests cooperative cancellation. User code that ignores interruption can
  continue after the exceptional return; no forceful termination or join is added.
- F04 remains open: cancellation-token changes during a blocking wait are not yet
  guaranteed to be observed promptly or classified correctly.
- A store borrow encloses one operation and its streams. It must not be closed by
  a separate owner while that operation continues using it.
- F08 remains open: provider construction and final release still execute under
  the resolver monitor. Move this work only with preservation of the new ownership
  invariants and additional race tests.
- A provider release that throws is reported and other shutdown releases are
  attempted. Automatic retry of an ambiguously completed release is not safe;
  successful-release statistics do not prove physical backend cleanup after an
  exception.
- F09, F12 and F13 remain P1 work for phase 2. Publication database concurrency,
  Boot ordering and the Gradle consumer classpath are not qualified by phase 1.
- The full project is not declared release-ready. The archive is a source
  delivery with tests and evidence, not a built distribution.

## Commit message

    fix(runtime): balance artifact store leases and cancel orphaned branches

    Return one provider lease per cache entry, including shared store identities.
    Retain operation borrows through publication and artifact-stream cleanup.
    Cancel outstanding parallel futures on abnormal exit and preserve causes.
    Add ownership and failure-path regressions; document Gradle validation limits.

Only this phase is implemented in this delivery. The roadmap contains one
proposed commit message for each later phase.
