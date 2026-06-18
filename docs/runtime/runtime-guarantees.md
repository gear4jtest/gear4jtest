# Runtime guarantees and non-guarantees

Gear4J is a lightweight Java runtime library. The current MVP favors explicit, bounded, in-process behavior over hidden
durable infrastructure. This page summarizes the operational contracts that application developers should not have to
infer from implementation details.

## Event runtime

Status: Implemented.

`EventManager` is an in-memory, best-effort dispatcher. It uses a bounded queue and submits matching reactions to an
`ExecutorService`.

Guaranteed today:

- event queue capacity is bounded by configuration;
- events rejected because the queue is full are counted in runtime stats;
- reactions rejected by the executor are counted as dropped reactions;
- publisher MDC context is captured when an event is published and restored around asynchronous reaction execution.

Not guaranteed today:

- durable delivery;
- replay after process failure;
- exactly-once reaction execution;
- dead-letter handling;
- transactional hand-off to an external broker.

Durable eventing remains a separate subsystem concern and must not be assumed from the in-memory event runtime.

## Persistence runtime

Status: Implemented with bounded in-memory buffering.

JDBC station-log persistence uses per-run buffers, asynchronous batch flushes and a bounded flush-task backlog.

Guaranteed today:

- pending station logs per run are bounded;
- scheduled asynchronous flush tasks are bounded by `maxScheduledFlushTasks`;
- failed flushes restore drained records when possible before reporting the failure;
- JDBC statements created by Gear4J persistence apply the configured statement timeout when supported by the driver;
- shutdown stops accepting new flush scheduling, cancels periodic maintenance and performs a final blocking drain of
  active buffers.

Not guaranteed today:

- persistence can survive a process crash before buffered logs are flushed;
- final shutdown flush can succeed when the database is unavailable;
- user code is forcibly stopped by persistence shutdown;
- JDBC migrations provide the same feature set as Flyway or Liquibase.

If persistence is enabled without a `SensitiveDataRedactor`, payloads, contexts and results are persisted as-is. This is
acceptable for local development only unless the application data model is known to be non-sensitive.

## External RUN publication

Status: Implemented.

RUN publication through `AssemblyLineManager` validates the candidate artifact before inserting RUN metadata. The
validation path reads the artifact, translates it and compiles the generated Java source. If validation fails, no RUN
object is inserted and the latest RUN alias is not invalidated.

Not guaranteed today:

- validation does not execute the generated pipeline;
- validation does not instantiate the generated class or inject dependencies;
- validation does not sandbox trusted inline Java.

## XML and generated code

Status: Implemented.

Untrusted XML must use GEL-only definitions. Trusted XML can opt into inline Java, but trusted XML is equivalent to source
code and must be reviewed as such.

Guaranteed today:

- the XML parser is configured against XXE-style external entity loading;
- the default translator/generator rejects inline Java;
- the Gradle XML plugin is GEL-only by default and requires an explicit `trustedXml()` opt-in for inline Java;
- GEL does not support method-call syntax, type lookup, constructors, static access or Java class metadata traversal.

Not guaranteed today:

- inline Java is sandboxed;
- generated Java is safe when XML comes from an untrusted source and trusted mode is enabled;
- GEL can safely evaluate arbitrary rich domain objects with side-effecting accessors.

GEL property paths are intended for maps, records and JavaBean DTOs. Arbitrary zero-argument methods are not treated as
readable properties.

## Cancellation and timeouts

Status: Partially implemented.

Timeouts bound waiting behavior in the Gear4J runtime. They do not magically stop arbitrary user code that ignores
interruption or keeps blocking on external resources.

Application operators and processors should be written cooperatively:

- respect thread interruption;
- use their own I/O timeouts;
- avoid unbounded blocking calls;
- keep retry/backoff policies explicit.
