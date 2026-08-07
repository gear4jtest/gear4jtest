# 0015 — Manager-owned station persistence batching

## Status

Accepted.

## Context

The core `PersistenceExtension` and the JDBC `DatabaseExecutionManager` both
historically buffered terminal station snapshots. The extension grouped records
for `appendAll(...)`, then the JDBC manager copied the same records into its own
per-run queue before scheduling database batches. This duplicated ownership,
made the effective flush point depend on two unrelated thresholds and delayed
failure attribution.

Iterators and assembly-line calls produce ordinary station lifecycle snapshots.
They must not implement their own persistence batching or append paths.

## Decision

The persistence manager is the only component allowed to buffer and batch
station snapshots:

- `PersistenceExtension` emits every start and terminal snapshot exactly once
  through `RunPersistenceManager.append(...)`;
- before `end(run)`, the extension invokes `flush(runId)`;
- `appendAll(...)` remains available for explicit bulk producers, but the
  lifecycle orchestrator does not use it;
- `PersistenceConfiguration.stationLogFlushThreshold(...)` configures an
  assembly-line default;
- `RunRequest.persistence(...)` can replace that configuration for one run;
- request configuration takes precedence over assembly-line configuration,
  which takes precedence over the persistence manager default;
- inline children and iterator items share the current run configuration;
- nested runs inherit the parent's effective persistence configuration;
- the JDBC manager stores the effective threshold in each run buffer and uses it
  for normal, periodic, final and shutdown drains;
- a per-run threshold cannot exceed the manager's bounded
  `maxPendingLogsPerRun` capacity.

JDBC persistence continues to write drained records through
`DatabaseAssemblyRunRepository.saveOperationRecordsBatch(...)`.

## Consequences

- There is one owner for buffering, scheduling, retry and failure state.
- Each `StationLogRecord` snapshot crosses the lifecycle-to-persistence boundary
  once, including records created inside iterators and nested assembly lines.
- A `RUNNING` snapshot can wait for the manager threshold or periodic flush; run
  completion still blocks until pending snapshots have been flushed.
- Provider-neutral managers may ignore per-run tuning by relying on the default
  `RunPersistenceManager.start(run, configuration)` implementation.
- Checkpoint/resume semantics and special policies for extremely large assembly
  lines remain future work.
