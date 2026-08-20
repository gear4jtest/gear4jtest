# Gear4J production readiness checklist

Gear4J is designed as an embeddable Java pipeline runtime. Before using it in a
production application, review the following operational boundaries.

## Runtime events

The default event runtime is in-memory and best-effort. It is appropriate for
local observers, metrics enrichment and non-critical side-compute reactions. Its
per-run queue accounting is bounded by default; saturated runtimes drop new
events and expose process-wide drop counts through `EventRuntimeMetrics.snapshot()`. Dispatch
is multiplexed by a shared in-process dispatcher instead of one dispatcher thread
per run, while reaction execution remains controlled by the configured reaction
executor. The default shutdown mode is `WAIT_FOR_DRAIN`, so `execute(...)` may
wait for accepted reactions until the shutdown timeout expires. Choose `DETACH_AND_DRAIN`, for
example through `RuntimeConfiguration.detachAndDrainDefaults()`, deliberately if
returning the pipeline result quickly matters more than waiting for best-effort
reaction drain. It must not be used as a business-critical
delivery guarantee. If durable delivery is required, use a dedicated outbox or
external broker design and keep handlers idempotent.

Built-in station events discard input and output payloads by default. Keep that
default when reactions only need identity, status and timing. Raw payload
forwarding through `EventPayloadPolicy.passthrough()` is an explicit unsafe
choice; prefer a selective or redacting policy when business values are needed.
This policy does not sanitize application-defined custom events.

## XML and generated Java

XML definitions using inline Java or operator class names must be treated as
trusted source code. Do not enable `XmlOperationChainTranslator.trusted()` for
XML received from users or a BO.

Restricted XML needs both Gear4J Expression Language (GEL) and a mode-aware
`XmlOperatorCapabilityPolicy`. GEL has no reflection, class loading, file access,
network access or arbitrary method calls. The capability policy prevents the XML
from bypassing those restrictions by selecting any visible `Operator` class.
Use stable capability ids in `processingOperation/@type`, register only the
required TEST/RUN mappings in trusted application configuration, and keep the
default deny-all policy when no operator execution is intended.

GEL rejects Java object property access by default. Feed untrusted expressions
inert maps/snapshots, or configure an exact `PropertyAccessPolicy` only for
trusted types. Do not deploy the deprecated legacy bean policy as a permanent
compatibility setting; its warnings should be treated as migration inventory.

Promotion translates the stored candidate again with RUN capabilities. A
TEST-only capability therefore blocks promotion before RUN metadata is staged.
Review each registered operator and injected dependency as trusted application
code; Gear4J does not sandbox them.

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
- configure a `PayloadCloner` for retained mutable business values. Persistence
  recursively snapshots standard containers after redaction and rejects unknown
  mutable leaf types when no suitable cloner is available;
- set `gear4j.persistence.redaction-mode=REQUIRE` in Spring Boot deployments
  that must fail fast without an explicit redactor;
- tune `gear4j.persistence.batch-size`, `max-pending-logs-per-run`,
  `max-active-runs`, `max-buffered-station-logs`, `flush-threads`,
  `max-scheduled-flush-tasks` and
  `jdbc-statement-timeout` for expected volume and database latency;
- normal run operations do not hold a manager-wide lock during JDBC I/O.
  Independent runs may call the repository concurrently, while each run buffer
  serializes its own drains. Any custom repository or datasource supplied to the
  manager must therefore be thread-safe;
- the built-in core `PersistenceExtension` emits each station lifecycle snapshot
  once; the persistence manager alone owns batching and is flushed before the run
  ends;
- tune the manager default with `gear4j.persistence.batch-size`, an assembly-line
  default with `PersistenceConfiguration.stationLogFlushThreshold(...)`, or a
  single execution with `RunRequest.persistence(...)`; per-run values must not
  exceed `max-pending-logs-per-run`;
- provide a durable `RejectedPersistenceRecordHandler` when isolated invalid
  station logs must remain recoverable; the default only logs safe metadata;
- monitor failed flushes, rejected appends, quarantined station logs, active
  buffers and `gear4j.persistence.flush.duration{trigger,outcome}` p95/p99;
- keep persistence history queries paginated. `PageRequest` is intentionally
  capped at 1,000 rows per call to avoid accidental large reads. The external
  JDBC repositories also expose paginated variants for operation-chain objects
  and tags.

## External RUN promotion

Before an external TEST definition is promoted to RUN, Gear4J translates and compiles the candidate artifact. Treat this
as a release gate for generated source validity, not as a full execution test: promotion validation does not instantiate
the generated class, inject dependencies or run the pipeline. Keep a separate application-level validation flow for
semantic tests and dependency availability.

## Generated-source compilation

Runtime generated-source compilation is isolated from caller threads and has a
30-second end-to-end deadline by default. The default executor has one worker and
32 queue slots. Tune `GeneratedCompilationConfiguration` only after considering
compiler thread safety and expected publication/load concurrency.

The same configuration rejects generated UTF-8 source above 4 MiB and cumulative
bytecode above 8 MiB per compilation by default. These are hard admission
limits, not cache settings. Monitor
`GeneratedCompilationStats.limitRejectedCompilations()` and review each increase
as either an invalid definition or an explicit capacity decision.

Close `AssemblyLineManager` during application shutdown. Alert on increasing
`GeneratedCompilationStats.timedOutCompilations()` or
`rejectedCompilations()`, and on an `activeCompilations()` value that remains
non-zero after a timeout. Cancellation is best-effort for custom compilers that
ignore interruption; their late results are discarded, but the worker remains
occupied until the delegate returns.

## Generated assembly-line loading

Runtime loading has a separate 60-second end-to-end deadline by default. It
includes the bounded executor queue, artifact lookup/read, translation,
compilation, class loading, construction and dependency injection. Configure
`GeneratedLoadingConfiguration` independently when the compiler budget or
artifact backend latency changes. Keep the loading deadline greater than the
normal compilation budget so valid work retains time for instantiation and
injection.

Alert on increasing `GeneratedLoadingStats.timedOutLoads()` or
`rejectedLoads()`, any `artifactIntegrityFailures()`, a persistently full queue,
and active work that remains after a timeout. Per-phase counters identify
whether artifact access, translation, compilation, class loading, construction
or injection consumed the budget or failed. A timeout wakes all callers sharing
the same concrete loader ID and permits a later retry; a late result is not
registered. Interruption remains cooperative, so untrusted or hostile custom
code requires process/container isolation.

## Artifacts

Generated pipeline artifacts are generally expected to be small XML/source
bundles. `ArtifactStore.put(InputStream)`, composite-store verification and
`AssemblyLineManager` enforce a 5 MiB default artifact limit. Use
`ArtifactStore.put(InputStream, maxBytes)` and the manager constructor with
`maxArtifactSizeBytes` to choose a stricter or larger application limit.
`ArtifactStore.UNLIMITED_SIZE` disables only a caller-specific limit and never
overrides a backend's configured bound.

Configure FILESYSTEM roots as private, application-owned directories. Gear4J
rejects symbolic links in the root path and artifact tree, applies owner-only
POSIX permissions when available, publishes immutable entries without
replacement and verifies SHA-256 before exposing file content. The verified
content is returned as an in-memory snapshot. FILESYSTEM stores therefore apply
`maxArtifactSizeBytes` to writes and reads, default to 5 MiB and reject an
unbounded configuration. A larger finite value must remain consistent with the
application's memory budget. Alert on
`ArtifactStoreMonitor#snapshotStats().cleanupFailures()`.

The database store applies the same 5 MiB default to direct byte-array writes,
streamed writes and reads. Configure `maxArtifactSizeBytes` in DATABASE store
properties when a different bound is required. Configure `spoolDirectory` to a
private application-owned path. Gear4J applies and verifies owner-only POSIX
permissions or an owner-only ACL and fails store initialization when neither
mechanism can prove confidentiality. `requirePrivatePermissions` defaults to
`true`; set it to `false` only for a directory whose equivalent isolation is
provisioned and verified outside Gear4J. The managed spool defaults to a 100 MiB
quota and deletes `.tmp` residues older than 24 hours when a store is initialized.
Recent residues count toward the quota.

Treat the managed spool as temporary workspace, not as a recovery queue. It does
not record a destination or operation and therefore never replays `.tmp` files
after restart. A crash can leave recent bytes charged to the quota until they
become stale and a later initialization deletes them. The cleanup age is a
retention control, not an RPO or delivery deadline. For synchronous database
artifact writes, no successful acknowledgement exists until the database write
returns. For composite `ASYNC_FALLBACKS`, the acknowledged durability boundary
is the primary store: every fallback copy still queued or executing can be lost
on a JVM crash. A single quota-accounted spool copy and executor task serve all
fallbacks for one artifact. Saturation rejects that task after primary success
instead of running fallback I/O on the caller thread. Select `SYNC_ALL` when
waiting for every fallback is required; use a durable replication service or
outbox when queued copies themselves must survive restart.

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

Exercise restart recovery before production: stage two unique publications,
persist the expected hash for only one, reconstruct the configuration,
publication and artifact-store objects, then run reconciliation after the grace
period. The present hash must become visible with its tags, the missing hash
must remain invisible and be conditionally aborted, no stage may remain, and a
second pass must inspect zero stages. Use a durable JDBC publication repository;
the in-memory implementation cannot survive process restart.

Artifact-store booleans accept only `true` or `false`; replication and
self-healing require at least one complete `fallback.N.type` group. Treat a
startup rejection as a configuration defect rather than silently disabling the
requested durability behavior.

`mode.write=ASYNC_FALLBACKS` guarantees only that a successful return represents
an accepted primary write. Fallback scheduling and writes are best effort:
rejections and failures are logged after primary success without failing the
caller. Alert on these warnings and select `SYNC_ALL` when completion of every
fallback must be awaited. No composite mode makes independent stores
transactionally atomic.

## Metrics and health

`gear4jtest-micrometer` exposes counters and duration timers. Keep metric tags
low-cardinality in production; avoid unbounded operation or branch identifiers as
metric labels for dynamically generated pipelines.

Track `gear4j.branches.rejected` separately from terminal branch failures and
graph `gear4j.branches.duration{status}` percentiles for executed branches.
Synthetic branches contribute to `completed{status}` but do not emit a duration.
The persistence flush histogram has only closed `trigger` and `outcome` tags;
async samples include executor queue delay.

Alert on any artifact-integrity failure, sustained generated-loading or
compilation queues, executor rejections/timeouts, classloader registration
rejections and artifact-spool quota/cleanup failures. A single transient queue
sample is not necessarily an incident; cumulative rejection, timeout and
integrity counters should never be silently increasing.

When Spring Boot Actuator is present and JDBC persistence is enabled, put
`gear4jPersistenceLiveness` in the liveness group and
`gear4jPersistenceReadiness` in the readiness group. Liveness is process-local;
readiness checks current database connectivity, backlog size/age and recovery
after a failed flush. Do not restart an otherwise live process solely because the
database is temporarily unavailable.
The Gear4J connectivity-probe timeout bounds the complete readiness call,
including pool acquisition. Configure the datasource/pool
connection-acquisition timeout as well: it reclaims the single daemon probe
worker if a JDBC call ignores interruption and protects normal writes, which do
not run through the readiness worker.

The default lifecycle metrics omit pipeline, operation and branch identifiers.
If those dimensions are needed, use a reviewed finite allowlist; unknown values
are aggregated under `other`. Event and reaction drop metrics remain aggregate
and tagless.

Generated infrastructure metrics use only finite framework-owned `phase`,
`outcome`, `result`, `operation` and `trigger` values. They never include hashes, pipeline
IDs, exception messages or business data. The Spring Boot starter auto-binds
only a unique candidate; bind multiple managers or stores explicitly instead of
adding a raw bean name as a tag.

For the in-memory event runtime, monitor queued events, remaining queue capacity,
dropped events, dropped reactions, pending reactions and in-flight reactions.
Pending/in-flight reactions after shutdown usually mean user code ignored
interruption or blocked on an external resource longer than the configured
shutdown timeout.

JDBC persistence uses the same end-to-end shutdown-budget principle. A driver
call that ignores interruption may continue on a daemon worker, but the manager
returns a conservative report instead of blocking the application shutdown.
Configure the connection-pool acquisition timeout separately for normal writes.

## JDBC execution-history query plans

The mandatory JDBC matrix qualifies the six execution-history reads at 20,000
runs and 10,000 station logs per dialect job. Keep the generated
`sql-plan-qualification/<dialect>.md` reports with release evidence and review
the reference-index selection, any observed full scan and measured p95 before
materially increasing retention or page sizes. An alternative natural plan is
not automatically a defect when the engine selects a narrower predicate or
foreign-key index; the report retains the raw plan for that review.

The built-in two-second ceiling is intentionally loose and detects only a
catastrophic regression on shared CI. It is not a production latency SLO.
Production sizing still requires plans and timings with the application's data
distribution, retention window, pool, database configuration and network.

The V1 schema indexes match complete deterministic ordering:

- `(assembly_line_id, start_time, id)` for assembly-line history;
- `(status, start_time, id)` for status history;
- `(start_time, id)` for global history;
- `(assembly_line_execution_id, parent_log_id, start_time, id)` for root/child
  station-log pages;
- `(assembly_line_execution_id, start_time, id)` for all logs in a run.


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

The default `InMemoryClassLoaderRegistry` is bounded to 256 loaders and 64 MiB
of cumulative generated-bytecode weight. It evicts least-recently-used
unaliased loaders. Aliased loaders are protected from automatic eviction, but a
new registration is rejected rather than exceeding the bytecode limit when
every eviction candidate is protected. Monitor `RegistryStats.bytecodeWeightBytes()`,
`maxBytecodeWeightBytes()` and `rejectedLoaders()`. Tune both count and weight
for applications with frequent TEST/RUN version churn or long rollback windows.

The in-process compiler boundary limits Gear4J-owned source, heap bytecode and
classloader retention. It is not an OS sandbox. If definitions are genuinely
hostile, compile them in a separate process or container with explicit heap,
CPU, wall-clock and filesystem/network limits.

## Shared event dispatcher capacity

The process-wide in-memory event dispatcher uses a bounded non-blocking queue. Its default capacity is 4,096 lightweight
drain tasks and it rejects new tasks when saturated; the affected `EventManager` drops its pending best-effort events and
increments `EventRuntimeStats.droppedEvents()`. Each admitted task drains at most 64 events from one run before a
non-empty run is re-enqueued at the tail. This prevents an asymmetric loud run from monopolizing a dispatcher worker,
but it is not a wall-clock latency SLO.

The startup-only system property `gear4j.event.dispatcher.queue-capacity` can override the shared capacity with a
strictly positive integer. Invalid values fall back to 4,096 with a warning. Size this queue from observed concurrent run
counts, not from the number of business events: one scheduled drain task can process up to 64 events from one manager.

## Experimental assembly-line cache

The experimental in-memory assembly-line cache is bounded by entry count and configurable weight. TTL cleanup runs on
access and write paths. Cache values are cloned on write and read through an explicit `PayloadCloner`; the safe default
rejects unknown mutable types. Monitor `AssemblyLineCacheStats` for rejected writes, capacity/TTL evictions, hit ratio,
estimated weight and load-time growth.

## Final build and dependency-security gates

Production and Maven Central candidates require:

- Java 17 toolchains and `--release 17` for all Java compilation tasks;
- OWASP Dependency-Check with a CVSS 7.0 failure threshold;
- expiring, reviewed vulnerability suppressions only;
- reproducible archive ordering and timestamps;
- a two-build SHA-256 comparison of staged JAR, POM and Gradle module metadata artifacts;
- the complete PostgreSQL, MySQL, MariaDB and Oracle matrix.

Advanced Gradle dependency locking and checksum-verification metadata are deliberately deferred for the 1.0 line.
They remain a post-1.0 hardening item and are not required by CI or release at this stage.
