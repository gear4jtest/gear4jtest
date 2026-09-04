# Event runtime architecture

## Status

Implemented as in-memory best-effort runtime.

## Intent

The event runtime is a lightweight in-process mechanism for asynchronous reactions to runtime events.

It is suitable for:

- side-compute;
- local observability;
- non-critical enrichment;
- notifications that may be dropped under failure or saturation.

## Current semantics

The current runtime is deliberately best-effort.

Application code publishes through the run-scoped `EventPublisher` capability exposed by `ExecutionServices`. The
concrete `EventManager` owns queueing, reaction dispatch and shutdown, and remains an internal runtime type.

The event path is:

1. a station publishes an event;
2. the run-local `EventManager` reserves capacity in its bounded in-memory queue accounting;
3. a lightweight dispatch task is submitted to the shared in-process event dispatcher;
4. the shared dispatcher invokes the run-local dispatch task for a slice of at most 64 events, then re-enqueues the run
   at the tail when more events remain;
5. matching subscriptions are submitted to the run's configured `ExecutorService`;
6. accepted reactions run asynchronously;
7. events rejected by run-local queue accounting and reactions rejected by the executor are counted as dropped and
   produce rate-limited representative logs.

There is no durable hand-off, replay log or persistent acknowledgement.

## Payload confidentiality

Built-in station events discard their input and output payloads by default.
Identifiers, statuses and timing remain available to reactions. Applications
that deliberately need raw business values must configure
`EventPayloadPolicy.passthrough()` explicitly; selective allowlists and
`EventPayloadPolicy.redacting(...)` are safer choices when only part of a
payload is required.

The same policy applies to built-in events consumed by side-computes. A
side-compute that reads `StationFinishedEvent#getInput()` or `getOutput()` must
therefore opt in to a policy that retains the required value. Side-computes
that use only identifiers, status or timing do not need raw payload access.

This default is part of the core event definition, so it also applies to
engines created through the Spring and Spring Boot integrations. User-created
custom events remain under the application's responsibility.

## Shutdown behavior

`RuntimeConfiguration` defaults to `ShutdownMode.WAIT_FOR_DRAIN`. With this mode, once the pipeline itself has completed,
`AssemblyLineExecutor.execute(...)` waits for already accepted event reactions to drain until the configured shutdown
timeout expires. A single monotonic deadline covers reaction drain, owned-executor termination and forced shutdown; these
steps do not each restart the timeout. This preserves a simple "result returned after reactions drained or timed out"
contract, but it means an otherwise asynchronous event subscription can still make the caller wait at the end of the run.

The run-local `EventManager` owns queueing, dispatch and reaction accounting. The package-private
`EventRuntimeShutdown` owns shutdown-mode interpretation, the shared deadline and executor ownership. This separation is
internal: it does not change `EventManager.ShutdownHandle` or the best-effort delivery contract.

The default reaction executor is a process-wide, library-owned, bounded daemon pool. Its core workers retire after 60
seconds without work and are recreated on demand; run shutdown must not terminate this shared default. A reaction
executor supplied by the application is caller-owned and is never shut down by Gear4J. A per-run executor created by an
application-supplied factory is owned by that run and follows the configured shutdown deadline.

Use `ShutdownMode.DETACH_AND_DRAIN` when the application wants to return the pipeline result before best-effort reactions
finish. `RuntimeConfiguration.detachAndDrainDefaults()` exists as a readable shortcut for this common best-effort mode.
Detached mode is still not durable delivery: reactions may already have been dropped under saturation, and run-scoped
resources are cleaned up after `detachCleanupTimeout` even if user reaction code is still blocked. Timeout cleanup uses
a dedicated single-thread daemon scheduler; normal reaction completion cancels and removes its pending timeout so
completed runs are not retained until the full delay expires. This deadline scheduler is process-scoped because a
scheduled cleanup must remain eligible after `execute(...)` has returned and `AssemblyLineEngine` has no close lifecycle;
its daemon thread does not prevent JVM shutdown.

## What the runtime does not guarantee

The shared dispatcher removes the previous "one dispatcher thread per run" cost. Its 64-event service slice prevents a
continuously loud run from draining its entire queue before another scheduled run receives service. This is a fairness
bound in units of admitted events, not a wall-clock latency guarantee: reaction-executor saturation and slow application
reactions can still increase end-to-end latency. The dispatcher does not make the runtime durable or globally ordered.
Each run still owns its own subscriptions, shutdown mode, counters and reaction executor configuration.

The current event runtime does not provide:

- durable storage;
- guaranteed delivery;
- exactly-once execution;
- replay;
- retry after process crash;
- dead-letter handling;
- external transport delivery guarantees.

## Statistics

`EventManager.snapshotStats()` exposes runtime counters such as:

- published events;
- dispatched events;
- submitted reactions;
- dropped events;
- queued events;
- completed reactions;
- dropped reactions;
- failed reactions.

Use these counters for observability. Do not treat them as durable audit records.

`EventRuntimeMetrics.snapshot()` adds a tag-free JVM-wide view across all run-local managers. It aggregates dropped
events/reactions, shared-dispatcher rejections, current queued/in-flight work and queue-to-dispatch latency. The Spring
Boot starter registers these process gauges automatically when Micrometer is enabled; they are operational signals, not
durable audit records.

Repeated rejection and failure logs are bounded independently from these counters. See the
[runtime logging strategy](logging.md); alerting must use the exhaustive metrics rather than log-line counts.

## Durable eventing

Durable delivery should be handled by a separate subsystem or module.

Possible future designs include:

- JDBC outbox;
- local durable queue;
- Kafka publisher;
- SQS publisher;
- RabbitMQ publisher.

A durable design needs explicit decisions about serialization, retries, dead-letter handling, idempotency and
transactional boundaries.

Do not turn `EventManager` itself into a broker abstraction.
