# Durable event/outbox SPI

## Status

Partially implemented.

Gear4J currently provides the durable event contracts and a generic dispatcher.
It does **not** yet provide a production-ready JDBC outbox store or a fully wired
module that persists every runtime event automatically.

## Relationship with the in-memory event runtime

The existing Gear4J event runtime remains in-memory and best-effort. It is
appropriate for local observers, side-compute, metrics and non-critical
notifications.

Durable event delivery is a separate concern. It must not be hidden inside
`EventManager`, because durable delivery needs storage, retries, ownership rules,
claiming, dead-letter handling and idempotency guidance.

## Best-effort external forwarding

The `event.transport` package provides a lightweight transport boundary:

- `EventEnvelope`
- `EventEnvelopeMapper`
- `ExternalEventTransport`
- `PublishResult`
- `ExternalTransportReaction`
- `ExternalSubscriptions`

This path can forward runtime events to Kafka, SQS, RabbitMQ, HTTP, an outbox, or
another transport. If it is plugged into the current in-memory `EventManager`, it
remains best-effort:

- a JVM crash can lose unpublished events;
- executor saturation can drop reactions;
- transport failure does not imply durable retry;
- no replay is guaranteed.

Use this for observability or non-critical integrations.

## Durable outbox SPI

The `event.durable` package provides an outbox-style contract based on persisted
`EventEnvelope` values:

- `DurableEventEnvelopeStore`
- `StoredEventEnvelope`
- `DurableEventStatus`
- `DurableEventPublisher`
- `OutboxDispatcher`

The intended flow is:

```text
runtime event
  -> EventEnvelopeMapper
  -> DurableEventEnvelopeStore.append(...)
  -> persisted PENDING envelope
  -> OutboxDispatcher.claimPending(...)
  -> ExternalEventTransport.publish(...)
  -> markPublished(...) or markFailed(...)
```

This design targets at-least-once delivery. Consumers must be idempotent because
an envelope may be retried or replayed after a crash.

## What is not implemented yet

The current phase deliberately stops at the SPI and generic dispatcher. Missing
pieces include:

- `JdbcDurableEventEnvelopeStore`;
- dialect-specific SQL for claiming pending envelopes safely;
- retry backoff policy;
- dead-letter state and retention policy;
- metrics for pending, published, failed and dead-letter envelopes;
- transaction-boundary guidance for publishing an event in the same transaction
  as a business/persistence change;
- automatic wiring between selected runtime events and the durable outbox.

## Future JDBC outbox direction

A future JDBC implementation should own a table similar to:

```text
gear4j_event_outbox
  id
  event_type
  execution_id
  station_execution_id
  partition_key
  content_type
  schema_version
  payload
  headers
  status
  attempt_count
  next_attempt_at
  claimed_by
  claimed_until
  created_at
  updated_at
  last_error
```

Claiming must be dialect-aware. PostgreSQL can use `FOR UPDATE SKIP LOCKED`, but
other databases require different SQL. This is another reason durable delivery
must remain a dedicated subsystem rather than a hidden behavior of the current
best-effort runtime.


## Retry and dead-letter policy

`OutboxDispatcher` now accepts an `OutboxDispatchPolicy`. The default policy is
still deliberately small: it retries retryable failures up to a bounded number of
attempts with exponential backoff metadata. Stores that support delayed retry can
use the `retryDelay` argument passed to `markFailed(...)`; simpler stores can
ignore it and only use the retryable/terminal flag.

This is still not a full broker. A production durable event module must provide a
real store implementation, claim ownership rules, dead-letter visibility,
idempotency guidance and operational cleanup. Until then, the durable event API
should be treated as experimental.
