# Gear4J production readiness checklist

Gear4J is designed as an embeddable Java pipeline runtime. Before using it in a
production application, review the following operational boundaries.

## Runtime events

The default event runtime is in-memory and best-effort. It is appropriate for
local observers, metrics enrichment and non-critical side-compute reactions. It
must not be used as a business-critical delivery guarantee. If durable delivery
is required, use a dedicated outbox or external broker design and keep handlers
idempotent.

## XML and generated Java

XML definitions that contain inline Java expressions or generated Java logic must
be treated as trusted source code. Do not accept arbitrary XML from users or a BO
and compile it into the application JVM.

For untrusted or semi-trusted configuration, the intended security boundary is the
Gear4J Expression Language (GEL): a restricted expression language with no
reflection, class loading, file access, network access or arbitrary method calls.
A minimal GEL parser/evaluator exists in `gear4jtest-xml`, but XML generation still
uses trusted Java snippets today. Keep inline Java XML behind trusted provenance
and code review until XML/GEL integration is completed.

## Persistence

When JDBC persistence is enabled:

- configure an explicit `gear4j.persistence.dialect`;
- decide deliberately whether Gear4J may create/migrate its schema with
  `gear4j.persistence.auto-create-tables`;
- configure a `SensitiveDataRedactor` when payloads may contain PII, secrets or
  sensitive business data;
- tune `gear4j.persistence.batch-size`, `max-pending-logs-per-run` and
  `flush-threads` for expected volume;
- monitor failed flushes, rejected appends and active buffers.

## Artifacts

Generated pipeline artifacts are generally expected to be small XML/source
bundles. Do not use the default artifact APIs for unbounded large files without a
size policy. The `ArtifactStore.put(InputStream, maxBytes)` API and
`AssemblyLineManager` constructor with `maxArtifactSizeBytes` are available to
fail fast when an artifact exceeds the application limit.

## Metrics and health

`gear4jtest-micrometer` exposes counters and duration timers. Keep metric tags
low-cardinality in production; avoid unbounded operation or branch identifiers as
metric labels for dynamically generated pipelines.

When Spring Boot Actuator is present and JDBC persistence is enabled, the starter
contributes a Gear4J persistence health indicator with current persistence buffer
and flush statistics.

## Shutdown and cancellation

Executor-backed work is cooperative. Thread interruption and `Future.cancel(true)`
only stop operators that are written to observe interruption or a cancellation
signal. Long-running user code should be interruption-aware and should not rely on
Gear4J forcibly terminating arbitrary blocking work.
