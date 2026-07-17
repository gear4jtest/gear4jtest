# Gear4J production readiness checklist

Gear4J is designed as an embeddable Java pipeline runtime. Before using it in a
production application, review the following operational boundaries.

## Runtime events

The default event runtime is in-memory and best-effort. It is appropriate for
local observers, metrics enrichment and non-critical side-compute reactions. Its
per-run queue accounting is bounded by default; saturated runtimes drop new
events and expose the drop count through `EventManager.snapshotStats()`. Dispatch
is multiplexed by a shared in-process dispatcher instead of one dispatcher thread
per run, while reaction execution remains controlled by the configured reaction
executor. The default shutdown mode is `WAIT_FOR_DRAIN`, so `execute(...)` may
wait for accepted reactions until the shutdown timeout expires. Choose `DETACH_AND_DRAIN`, for
example through `RuntimeConfiguration.detachAndDrainDefaults()`, deliberately if
returning the pipeline result quickly matters more than waiting for best-effort
reaction drain. It must not be used as a business-critical
delivery guarantee. If durable delivery is required, use a dedicated outbox or
external broker design and keep handlers idempotent.

## XML and generated Java

XML definitions that contain inline Java expressions or generated Java logic must
be treated as trusted source code. Do not accept arbitrary XML from users or a BO
and compile it into the application JVM.

For untrusted or semi-trusted configuration, the intended security boundary is the
Gear4J Expression Language (GEL): a restricted expression language with no
reflection, class loading, file access, network access or arbitrary method calls.
A minimal GEL parser/evaluator exists in `gear4jtest-xml`, and XML conditions can now opt into it with `language="gel"`. Keep all other inline Java XML behind trusted provenance and code review.

GEL rejects Java object property access by default. Feed untrusted expressions
inert maps/snapshots, or configure an exact `PropertyAccessPolicy` only for
trusted types. Do not deploy the deprecated legacy bean policy as a permanent
compatibility setting; its warnings should be treated as migration inventory.

## Persistence

When JDBC persistence is enabled:

- configure an explicit `gear4j.persistence.dialect`;
- decide deliberately whether Gear4J may create/migrate its schema with
  `gear4j.persistence.auto-create-tables`;
- direct managers default to `SensitiveDataRedactor.discardSensitiveValues()`:
  identifiers/statuses/timestamps are retained, while contexts are empty and
  inputs, outputs and error messages are discarded;
- the Spring Boot starter also defaults to metadata-only `DISCARD` mode;
- configure a `SensitiveDataRedactor` before enabling selected payload capture;
  `SensitiveDataRedactor.none()` or Spring Boot `redaction-mode=DISABLED` are the
  preferred explicit unsafe choices that persist run context, inputs, outputs,
  station context and error messages as-is; deprecated explicit `WARN` retains
  the same raw-capture compatibility behavior with a warning;
- set `gear4j.persistence.redaction-mode=REQUIRE` in Spring Boot deployments
  that must fail fast without an explicit redactor;
- tune `gear4j.persistence.batch-size`, `max-pending-logs-per-run`,
  `flush-threads`, `max-scheduled-flush-tasks` and
  `jdbc-statement-timeout` for expected volume and database latency;
- normal run operations do not hold a manager-wide lock during JDBC I/O.
  Independent runs may call the repository concurrently, while each run buffer
  serializes its own drains. Any custom repository or datasource supplied to the
  manager must therefore be thread-safe;
- the built-in core `PersistenceExtension` persists station start snapshots
  immediately and batches terminal station snapshots with `appendAll(...)` before
  ending the run; use `terminalRecordBatchSize(1)` for the most immediate
  terminal snapshot persistence behavior;
- monitor failed flushes, rejected appends and active buffers;
- keep persistence history queries paginated. `PageRequest` is intentionally
  capped at 1,000 rows per call to avoid accidental large reads. The external
  JDBC repositories also expose paginated variants for operation-chain objects
  and tags.

## External RUN promotion

Before an external TEST definition is promoted to RUN, Gear4J translates and compiles the candidate artifact. Treat this
as a release gate for generated source validity, not as a full execution test: promotion validation does not instantiate
the generated class, inject dependencies or run the pipeline. Keep a separate application-level validation flow for
semantic tests and dependency availability.

## Artifacts

Generated pipeline artifacts are generally expected to be small XML/source
bundles. `ArtifactStore.put(InputStream)`, composite-store verification and
`AssemblyLineManager` enforce a 5 MiB default artifact limit. Use
`ArtifactStore.put(InputStream, maxBytes)` and the manager constructor with
`maxArtifactSizeBytes` to choose a stricter or larger application limit.
`ArtifactStore.UNLIMITED_SIZE` should be an explicit trusted-deployment choice,
not an accidental default.

The database store applies the same 5 MiB default to direct byte-array writes,
streamed writes and reads. Configure `maxArtifactSizeBytes` in DATABASE store
properties when a different bound is required. Configure `spoolDirectory` to a
private application-owned path; Gear4J applies owner-only POSIX permissions
when the filesystem supports them. The managed spool defaults to a 100 MiB
quota and deletes `.tmp` residues older than 24 hours when a store is
initialized. Recent residues count toward the quota.

Database artifact reads are lazy and keep a JDBC connection open until the
returned stream is closed. Use `Artifact#openStreamChecked()` in
try-with-resources. Monitor `ArtifactStoreMonitor#snapshotStats()` for byte,
latency, early-close and failure counters.
Monitor `ArtifactSpoolMonitor#snapshotSpoolStats()` for current occupancy, stale
cleanup, quota rejections and cleanup failures.

Because operation-chain artifacts may use non-database backends, no invalid
global foreign key is installed from `operation_chain_object` to
`artifact_store`. Schedule `ArtifactConsistencyChecker` for important assembly
lines and alert when its report is inconsistent.

External publication now stages metadata before writing an artifact. Schedule
`ArtifactPublicationReconciler` with a grace period longer than the slowest
expected artifact upload. Alert on reconciliation failures and on stages that
remain older than that grace period. A present artifact is committed; a missing
artifact is conditionally abandoned without deleting content from a shared
content-addressed store. Idempotent retries renew stage age and revision, so an
older reconciliation pass cannot abort an active retry. Store configuration is
fingerprinted in each stage; if configuration changes while a stage exists, the
stage is retained and reported instead of being checked against the new backend.
Avoid changing store configuration until old stages have been reconciled or
explicitly resolved. The generic SPI still cannot enumerate legacy store-only
objects.

Artifact-store booleans accept only `true` or `false`; replication and
self-healing require at least one complete `fallback.N.type` group. Treat a
startup rejection as a configuration defect rather than silently disabling the
requested durability behavior.

## Metrics and health

`gear4jtest-micrometer` exposes counters and duration timers. Keep metric tags
low-cardinality in production; avoid unbounded operation or branch identifiers as
metric labels for dynamically generated pipelines.

When Spring Boot Actuator is present and JDBC persistence is enabled, put
`gear4jPersistenceLiveness` in the liveness group and
`gear4jPersistenceReadiness` in the readiness group. Liveness is process-local;
readiness checks current database connectivity, backlog size/age and recovery
after a failed flush. Do not restart an otherwise live process solely because the
database is temporarily unavailable.
Configure the datasource/pool connection-acquisition timeout in addition to the
Gear4J connectivity-query timeout so a saturated pool cannot block readiness
indefinitely.

The default lifecycle metrics omit pipeline, operation and branch identifiers.
If those dimensions are needed, use a reviewed finite allowlist; unknown values
are aggregated under `other`. Event and reaction drop metrics remain aggregate
and tagless.

For the in-memory event runtime, monitor queued events, remaining queue capacity,
dropped events, dropped reactions, pending reactions and in-flight reactions.
Pending/in-flight reactions after shutdown usually mean user code ignored
interruption or blocked on an external resource longer than the configured
shutdown timeout.

JDBC persistence uses the same end-to-end shutdown-budget principle. A driver
call that ignores interruption may continue on a daemon worker, but the manager
returns a conservative report instead of blocking the application shutdown.
Configure the connection-pool acquisition timeout separately for normal writes.


## Worker concurrency

The default worker concurrency policy protects stateful worker instances with a
process-wide lock. This is the safest option when a `ResourceFactory` may return
non-thread-safe singleton operators, but it can create registry churn for
high-volume prototype operators. Use `LOCK_REUSED_WORKER_INSTANCE_ONLY` only when
non-reused stations are guaranteed to receive fresh execution-scoped operators,
stateless operators or thread-safe operators. Keep the default or
`ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE` when singleton sharing is possible.

## Shutdown and cancellation

Executor-backed work is cooperative. Thread interruption and `Future.cancel(true)`
only stop operators that are written to observe interruption or a cancellation
signal. Long-running user code should be interruption-aware, should poll the run
`CancellationToken` where practical and should not rely on Gear4J forcibly
terminating arbitrary blocking work.

When reusing a `RunRequest` as a template for several independent runs, prefer
`RunRequest.toIndependentBuilder()`. `toBuilder()` intentionally preserves the
original cancellation token and call-stack state for nested or otherwise coupled
execution scopes.

## Generated classloader cache

The default `InMemoryClassLoaderRegistry` is bounded and evicts least-recently-used unaliased loaders. Aliased loaders are protected from automatic eviction. Tune the registry capacity for applications with frequent TEST/RUN version churn or long rollback windows.

## Shared event dispatcher capacity

The process-wide in-memory event dispatcher uses a bounded non-blocking queue. Its default capacity is 4,096 lightweight
drain tasks and it rejects new tasks when saturated; the affected `EventManager` drops its pending best-effort events and
increments `EventRuntimeStats.droppedEvents()`.

The startup-only system property `gear4j.event.dispatcher.queue-capacity` can override the shared capacity with a
strictly positive integer. Invalid values fall back to 4,096 with a warning. Size this queue from observed concurrent run
counts, not from the number of business events: one scheduled drain task can process several events from one manager.

## Experimental assembly-line cache

The experimental in-memory assembly-line cache is bounded by entry count and configurable weight. TTL cleanup runs on
access and write paths. Cache values are cloned on write and read through an explicit `PayloadCloner`; the safe default
rejects unknown mutable types. Monitor `AssemblyLineCacheStats` for rejected writes, capacity/TTL evictions, hit ratio,
estimated weight and load-time growth.
