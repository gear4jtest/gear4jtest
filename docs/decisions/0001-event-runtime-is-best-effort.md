# 0001 - Event runtime is best-effort

## Status

Implemented.

## Context

Gear4J has an asynchronous event runtime used for local reactions, side-compute and observability.

The current implementation relies on in-memory queues and an executor. It does not persist events before hand-off and
does not maintain a durable retry/replay log.

## Decision

The core `EventManager` remains a lightweight, in-process, asynchronous, best-effort runtime.

It must not be documented or treated as guaranteed delivery.

## Consequences

This is acceptable for:

- local side-compute;
- observability callbacks;
- non-critical enrichment;
- best-effort notifications.

It is not acceptable for:

- guaranteed external publication;
- durable audit trails;
- exactly-once workflows;
- recovery after JVM crash;
- business-critical message delivery.

## Failure modes

Reactions may be lost if:

- the JVM crashes;
- the process is killed;
- the bounded in-memory event queue rejects new publications;
- the executor rejects submissions;
- shutdown cancels pending work;
- the reaction itself fails.

Built-in station-event payload mapping and publication are isolated from station execution: an ordinary mapping or
publication exception drops the event and emits a rate-limited warning, while JVM-level `Error` failures are not
swallowed. Once an event reaches `EventManager`, dropped events, dropped reactions and failed reactions are counted in
runtime statistics. A failure before that hand-off is represented by the warning only. Neither signal is a durable audit
record.

## Future direction

If Gear4J needs durable event delivery later, create a separate subsystem such as:

- JDBC outbox;
- local durable queue;
- Kafka publisher;
- SQS publisher;
- RabbitMQ publisher.

Do not overload `EventManager` with broker responsibilities.
