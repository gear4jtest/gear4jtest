# 7 September audit — phase 3: cancellation, results and failure cleanup

| Field | Value |
| --- | --- |
| Status | Implemented — Gradle and database qualification pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-12 |

This delivery implements findings **F04, F05, F06, F10, F11 and F14** from the
[7 September remediation plan](../roadmap/audit-remediation-2026-09-07.md).
The cumulative source baseline is the corrected phase 2 archive
`gear4jtest-20260908-phase-2.zip`, SHA-256
`900e10fa3c23366da28d0ff5a1d4127f0995ee4758148c981f73c423fdf0a4c7`.
It includes the later `restrictedCapabilities()` Gradle closure hotfix.

Java 17, Gradle 9.6.1, project names, dependency policy and existing V1 schemas
are unchanged. This archive does not contain phase 4 work.

## Implementation

Paths below are relative to the repository root; class names identify the
modified methods within the corresponding module's `src/main/java` tree.

| Finding | Location | Implemented behavior |
| --- | --- | --- |
| F04 | `gear4jtest-core`: `ParallelContainerBranchExecutor`, `SideComputeWaitProcessor`, `StationExceptionBoundaryRunner` | Parallel and side-compute waits observe cancellation in bounded polling slices under one deadline. Side-compute cancellation bypasses ordinary failure recovery and timeout fallback/ignore handling. |
| F05 | `gear4jtest-core`: `StationContextUtils.applyTransformer`, `WorkStationStrategy.doExecute` | Operator presence is resolved before invocation, allowing a successful null or Void result and forwarding null downstream. |
| F06 | `gear4jtest-core`: `AssemblyLineEngine.executeWithCallStack`, `RunResources` | Event-runtime ownership begins immediately after acquisition, covering context-policy and ID-generator failures as well as normal shutdown. |
| F10 | `gear4jtest-jdbc`: `JdbcSchemaMigrator.inMigrationTransaction`, `rollback`, `persistFailedMigration` | Migration and retry share transaction cleanup. Owned transactions roll back on Error, retain cleanup failures and avoid enabling auto-commit after uncertain rollback. |
| F11 | `gear4jtest-jdbc`: `JdbcStatementOptions`, `MigrationHistoryStore`, `MigrationLockStore`, `JdbcSchemaMigrator`; `gear4jtest-external-jdbc`: `OperationChainObjectJdbcOperations` | Newly created ordinary, prepared and generated-key statements are closed when timeout configuration fails. Close failures retain their causes. |
| F14 | `gear4jtest-core`: `AssemblyLineRunLifecycleInvoker`, `AssemblyLineExecutionResultMapper`, `AbstractStationLogState.addErrorHandlerException` | Completion-hook failures no longer erase the earlier run failure. Normalized station failures retain throwable causes, even when their messages are null. |

### Cancellation and timeout contract

Waits use slices of at most 50 ms, with the remaining duration taken from a
single monotonic deadline. An empty slice does not mean that the overall timeout
expired. Token cancellation is checked again before classifying a timeout.

The interval is a framework observation bound, not an end-to-end latency SLA.
Scheduling, callbacks and configured event shutdown can extend the return time.
Cancellation remains cooperative, and caller-owned executors are never shut
down. A branch that has already completed retains the existing race semantics.
An explicitly selected parallel `CancelPolicy` can still map child cancellation
to a failed parent.

The side-compute cancellation path does not itself resolve or cancel the shared
future, invoke a fallback, or execute the waiting operator. Normal run cleanup
subsequently handles unresolved run futures. Fatal errors from futures continue
to propagate; interruption retains the thread's interrupt flag.

### Pre-1.0 API adjustment

`StationContextUtils.applyTransformer(...)` changes from `Optional<Object>` to
`@Nullable Object`. External consumers of this helper must recompile and remove
Optional chaining. Checking whether an operator exists remains the job of
`getTransformer(...)`; invoking an absent operator throws `IllegalStateException`.
Public executor entry-point signatures are unchanged.

```java
Object output = StationContextUtils.applyTransformer(input, context);
// Null is a legitimate successful operator result.
```

This change does not make side-compute completion values nullable. See
[API contracts](../architecture/api-contracts.md) and
[runtime guarantees](../runtime/runtime-guarantees.md).

### Ownership and failure precedence

The engine acquires a closeable run-resource scope before context initialization.
It closes an acquired event runtime when initialization fails, and uses the
existing drain/detach policy once a context exists. MDC restoration remains
outside runtime cleanup so cleanup executes with the run's MDC when available.

When a critical completion hook fails, an earlier run failure stays primary;
later hook exceptions are suppressed on it. Later hooks see the same primary
failure in the trace. Self-suppression is guarded. A fatal completion `Error`
escapes and retains the earlier ordinary failure as suppressed.

Station-to-run normalization keeps the terminal trace message while attaching
the recorded throwable causes. It intentionally does not promise identity with
the original operator exception. Exceptions with null messages are now recorded
before the human-readable message handling returns early.

The JDBC transaction helper owns only connections whose initial auto-commit flag
is true. It never commits, rolls back or changes the flag of an already active
caller transaction. Explicit connections remain open; DataSource overloads
close the connections they acquire.

If owned rollback fails, the helper neither writes a failure marker nor enables
auto-commit. The owner must discard that uncertain connection. Cleanup failures
remain suppressed on the original failure, except that a fatal cleanup Error
takes precedence over an ordinary exception and retains that exception. A fatal
failure does not guarantee durable migration history. DDL implicit commits still
depend on the dialect; no schema changes or compatibility migrations are added.
See [JDBC transaction and recovery contracts](../architecture/jdbc-migrations.md).

## Regression sources

There are **21 new JUnit test methods**, representing **28 invocations** when
their declared enum/boolean parameter sets execute. Existing helper tests and
side-compute mocks were also adapted to the corrected contracts.

| Test class | New methods / invocations | Covered regression |
| --- | --- | --- |
| `ParallelContainerBranchExecutorTest` | 1 / 1 | Cancellation after entering the timed wait, branch interruption, caller executor remains usable |
| `SideComputeFlowIntegrationTest` | 1 / 3 | Cancellation during FAIL, FALLBACK and IGNORE waits, cancelled result/trace, no fallback or operator call |
| `SideComputeWaitProcessorTest` | 1 / 1 | Fatal future failure escapes unchanged |
| `AssemblyLineEngineOutcomeTest` | 2 / 2 | Void success and null propagation to the next operator |
| `AssemblyLineEngineInitializationCleanupTest` | 2 / 2 | Context-policy exception and ID-generator Error restore active event-runtime counts |
| `RunLifecycleExtensionTest` | 4 / 4 | Original cause with no message, start/completion precedence, reused exception and fatal completion |
| `JdbcSchemaMigratorFailureCleanupTest` | 6 / 11 | Migration/retry rollback ordering, rollback/restoration failures, caller transaction and DataSource connection ownership |
| `JdbcStatementOptionsTest` | 3 / 3 | Failed configuration closes once, fatal configuration cleanup, successful statement remains open |
| `JdbcSchemaMigratorFatalRollbackIT` | 1 / 1 | PostgreSQL transactional CREATE TABLE is rolled back when a following statement throws Error |

The PostgreSQL test uses Testcontainers and fault injection after real DDL. It
is authored but **has not run in this environment**. JDBC doubles alone cannot
prove real database transaction behavior.

## Observed validation

Machine-readable details, probe output and attempted commands are retained in
[phase 3 validation evidence](evidence/remediation-2026-09-12-phase-3-validation.json).

- Java 17 compilation of all 283 core production sources succeeded using five
  temporary SLF4J/JSpecify stand-ins. Compilation of the changed JDBC source
  closure also succeeded against the local compiled dependencies. This is a
  limited source check, not a build with the actual dependency graph.
- **29 standalone probe scenarios passed** against the production classes:
  11 runtime, 15 JDBC fault-injection and 3 parallel-wait scenarios. The latter
  include normal completion across several polling slices and a 200 ms overall
  timeout observed at 203 ms. Cancellation observations of 50–57 ms are local
  measurements, not performance guarantees.
- The probes also exercise failure-marker cleanup after migration failure,
  including failed initial rollback and fatal failure-marker persistence.
- Focused Gradle tests, `spotlessApply` and `check` were attempted. Each stopped
  before build startup while fetching Gradle 9.6.1 with `Network is unreachable`.
  No JUnit, Testcontainers, Spotless, JaCoCo or full Gradle gate is claimed to pass.
- Living-documentation metadata and archive integrity are checked locally at
  delivery. The actual Gradle metadata task remains part of the pending gates.

## Qualification commands

Run with Java 17, dependency access and Docker available:

```bash
./gradlew --no-daemon :gear4jtest-core:test :gear4jtest-jdbc:test :gear4jtest-external-jdbc:test
./gradlew --no-daemon :gear4jtest-jdbc:integrationTest --tests '*JdbcSchemaMigrator*IT'
./gradlew --no-daemon spotlessApply
./gradlew --no-daemon check
```

Review formatting output before committing. Retain actual JUnit and PostgreSQL
reports before treating phase 3 exit criteria as fully qualified.

## Commit message and remaining scope

```text
fix(core): preserve cancellation, nullable results and failure causes
```

Phase 4 remains **F07, F08 and F15**: log confidentiality, store contention and
documentation/release qualification. The implementation status of this phase
does not waive the connected validation gates from earlier phases.
