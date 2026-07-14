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

By default, event shutdown uses `WAIT_FOR_DRAIN`: the pipeline result is returned only after already accepted reactions
have drained or the shutdown timeout expires. Use `DETACH_AND_DRAIN` explicitly when best-effort reactions should outlive
the caller's `execute(...)` call; `RuntimeConfiguration.detachAndDrainDefaults()` is the shortcut for that explicit mode.

Side-compute waits are synchronous from the station's perspective. A station waiting for side-compute depends on the
event reaction executor having enough capacity to complete the corresponding side-compute reaction before the configured
timeout.

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

Direct persistence managers and the Spring Boot starter use a metadata-only
policy when no `SensitiveDataRedactor` is configured: contexts are empty and
inputs, results and error messages are discarded. `SensitiveDataRedactor.none()`
or Spring Boot `redaction-mode=DISABLED` are explicit opt-ins to raw capture.
`REQUIRE` fails startup without an effective redactor. The deprecated Spring
Boot `WARN` mode retains the former raw-capture behavior and emits a warning.

## Default identifier generation

Status: Implemented.

The default `IdGenerator` produces UUIDv7 values with per-thread monotonic
state. It reads wall time once per identifier and uses the UUIDv7 12-bit
sequence while time is unchanged or has moved backwards. After 4096 values in
one logical millisecond, it advances a thread-local logical timestamp by one
millisecond rather than waiting for the wall clock.

Guaranteed today:

- no spin-wait or clock-polling loop after sequence exhaustion;
- UUID version 7 and RFC 4122 variant bits;
- non-decreasing encoded timestamps within one generator thread;
- automatic return to wall-clock time when it moves beyond logical time.

Not guaranteed today:

- exact wall-clock timestamps during clock rollback or sustained frozen-clock
  generation;
- deterministic ordering between different threads or processes;
- absolute collision impossibility across processes.

See [ADR 0024](../decisions/0024-uuidv7-uses-bounded-logical-time.md)
for the selected clock-rollback tradeoff.

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
- GEL contexts use secure property access by default: expression evaluation reads inert map snapshots and rejects
  record/JavaBean access unless the exact runtime type and property were explicitly allowlisted.

Not guaranteed today:

- inline Java is sandboxed;
- generated Java is safe when XML comes from an untrusted source and trusted mode is enabled;
- the safety of a caller-provided custom `PropertyAccessPolicy`; it is trusted application code and becomes part of the
  GEL security boundary.

For untrusted definitions, pass maps or values created by `GearExpressionValues.snapshot(...)`. Record components and
JavaBean getters are rejected by default. Trusted callers can build an exact-type `PropertyAccessPolicy` allowlist. The
deprecated legacy policy exists only for a bounded migration period and emits warnings when it invokes an accessor.

## Cancellation and timeouts

Status: Partially implemented.

Timeouts bound waiting behavior in the Gear4J runtime. They do not magically stop arbitrary user code that ignores
interruption or keeps blocking on external resources.

Application operators and processors should be written cooperatively:

- respect thread interruption;
- poll `CancellationToken` during long loops or multi-step work;
- use their own I/O timeouts;
- avoid unbounded blocking calls;
- keep retry/backoff policies explicit.

A `CancellationToken` is one-shot state. Sharing one token between unrelated top-level runs couples their lifecycle:
cancelling one run cancels every run that reused the same token. `RunRequest.toBuilder()` preserves the token;
`RunRequest.toIndependentBuilder()` intentionally drops it so the engine can allocate a fresh token for the copied run.

## AssemblyLine graph immutability

Status: Implemented for station definitions.

A station graph is immutable after construction. Station identifiers, kinds, processors, error policies, skip rules,
fallback operators, metadata, flow configuration and container branch definitions are copied into final fields when a
station is built. Builders remain mutable while the graph is being assembled, but built stations must not be modified by
application code or by the runtime.

Runtime state is stored separately in execution contexts and traces. This keeps one `AssemblyLine` definition safely
reusable across runs and prevents concurrent executions from mutating shared station definitions.
