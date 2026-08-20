# Persistence and trace audit remediation

## Decision

The audit findings were valid. The most serious issue was not one isolated SQL
width mismatch, but the combination of incomplete global backpressure, a
failure state that permanently poisoned otherwise recoverable buffers, and run
finalization state that could be lost after one failed update. The remediation
therefore changes the runtime contract rather than adding narrow exception
handling around the existing queue.

## Implemented scope

- Identifier limits are aligned at 255 Unicode code points across model
  validation, JDBC preflight validation and every bundled V1 dialect schema.
- JDBC buffering now has independent per-run, global active-run and global
  retained-log limits. Drained in-flight records continue to count against the
  global limit until their outcome is known.
- A flush failure is diagnostic state, not a permanent poison flag. Unresolved
  records are restored in input order and later appends or maintenance passes
  may make progress.
- Proven record-data failures are isolated with batch bisection. Healthy subsets
  commit, while the isolated record is handed to a configurable rejection SPI.
  Transient, unknown and systemic failures remain retryable as a batch.
- Run finalization is retained as explicit buffer state and is retried by normal
  maintenance and shutdown. The active-run permit is released only after the
  final update succeeds.
- Container, sequence, if/else, iterator and inline assembly-line-call traces now
  expose their executed child logs through `StationTrace.getSubOperations()`.
- Station-scoped work resources use the station definition's identity, avoiding
  collisions when different station objects intentionally share the same ID.
- Micrometer and Actuator expose cumulative quarantine counts without putting
  record payloads or exception messages into metric tags or health details.

## Operational caveat

The built-in rejection handler is deliberately logging-only. It prevents one
bad row from blocking healthy rows, but it is not a durable recovery channel.
Applications that require complete auditability must configure a durable
`RejectedPersistenceRecordHandler`; if that handler cannot accept the record,
Gear4J keeps the record buffered and fails the flush.

## Compatibility

This project is not released, so the corrected identifier widths are part of V1
instead of compatibility V2 migrations. Any database created from an earlier
development snapshot must be recreated before this source version is used.
