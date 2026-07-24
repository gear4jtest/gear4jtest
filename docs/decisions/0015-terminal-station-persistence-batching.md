# 0015 — Terminal station persistence batching

## Status

Accepted.

## Context

The core persistence SPI exposes both `append(...)` and `appendAll(...)`, but the
built-in `PersistenceExtension` historically called `append(...)` for every
station start and every station completion. That kept the lifecycle path simple
and made persistence failures local to the station callback that observed them,
but it also amplified synchronous persistence calls for long pipelines and
iterators.

The JDBC manager already buffers station logs internally, but core-level
extensions and custom `RunPersistenceManager` implementations still benefited from
an explicit batching contract.

## Decision

`PersistenceExtension` now distinguishes start snapshots from terminal snapshots:

- station start snapshots are still persisted immediately through `append(...)`;
- terminal station snapshots are buffered per run and flushed through
  `appendAll(...)`;
- pending terminal snapshots are flushed before `manager.end(run)`;
- the default terminal batch size is `128`;
- `PersistenceExtension.builder(manager).terminalRecordBatchSize(1)` preserves
  one terminal flush per station when an application wants the most immediate
  completion-persistence behavior.

The JDBC `DatabaseExecutionManager#appendAll(...)` now appends batches to the
per-run buffer under one capacity check instead of delegating record by record to
`append(...)`.

## Consequences

- A long-running station is still visible as `RUNNING` if the process exits
  before completion.
- Terminal station records can be persisted in fewer SPI calls.
- A persistence failure while flushing buffered terminal records may be observed
  by a later station completion or by run completion rather than by the exact
  station that originally produced the terminal snapshot.
- Run completion remains blocked on all pending terminal station records being
  flushed before the run record is ended.
