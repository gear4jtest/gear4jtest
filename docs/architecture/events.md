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

The event path is:

1. a station publishes an event;
2. `EventManager` puts it in an in-memory queue;
3. the dispatcher thread takes the event;
4. matching subscriptions are submitted to an `ExecutorService`;
5. accepted reactions run asynchronously;
6. rejected reactions are logged and counted as dropped.

There is no durable hand-off, replay log or persistent acknowledgement.

## What the runtime does not guarantee

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
- completed reactions;
- dropped reactions;
- failed reactions.

Use these counters for observability. Do not treat them as durable audit records.

## Durable eventing

Durable delivery should be handled by a separate subsystem or module.

Possible future designs include:

- JDBC outbox;
- local durable queue;
- Kafka publisher;
- SQS publisher;
- RabbitMQ publisher.

A durable design needs explicit decisions about serialization, retries, dead-letter handling, idempotency and transactional boundaries.

Do not turn `EventManager` itself into a broker abstraction.
