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

## Persistence

When JDBC persistence is enabled:

- configure an explicit `gear4j.persistence.dialect`;
- decide deliberately whether Gear4J may create/migrate its schema with
  `gear4j.persistence.auto-create-tables`;
- configure a `SensitiveDataRedactor` when payloads may contain PII, secrets or
  sensitive business data; `SensitiveDataRedactor.none()` means run context,
  inputs, outputs, station context and error messages are persisted as-is;
- set `gear4j.persistence.redaction-mode=REQUIRE` in Spring Boot deployments
  that must fail fast without an explicit redactor;
- tune `gear4j.persistence.batch-size`, `max-pending-logs-per-run`,
  `flush-threads`, `max-scheduled-flush-tasks` and
  `jdbc-statement-timeout` for expected volume and database latency;
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

## Metrics and health

`gear4jtest-micrometer` exposes counters and duration timers. Keep metric tags
low-cardinality in production; avoid unbounded operation or branch identifiers as
metric labels for dynamically generated pipelines.

When Spring Boot Actuator is present and JDBC persistence is enabled, the starter
contributes a Gear4J persistence health indicator with current persistence buffer
and flush statistics.

For the in-memory event runtime, monitor queued events, remaining queue capacity,
dropped events, dropped reactions, pending reactions and in-flight reactions.
Pending/in-flight reactions after shutdown usually mean user code ignored
interruption or blocked on an external resource longer than the configured
shutdown timeout.


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
