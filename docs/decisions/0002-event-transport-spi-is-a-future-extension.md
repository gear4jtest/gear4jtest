# 0002 - Event transport SPI is a future extension

## Status

Future direction. Not implemented.

## Context

Applications may eventually want to forward Gear4J runtime events to external transports such as Kafka, SQS, RabbitMQ or
an outbox.

Runtime events are rich Java objects and may reference runtime payloads. They are not stable transport contracts by
themselves.

## Decision

If external forwarding becomes useful, introduce a small transport SPI instead of turning `EventManager` into a broker
abstraction.

Potential future types:

- `EventEnvelope`;
- `EventEnvelopeMapper`;
- `ExternalEventTransport`;
- `PublishResult`;
- `ExternalTransportReaction`.

## Runtime event vs transport envelope

A runtime event is an in-process Java object.

A transport envelope should be serialization-ready and stable. It should avoid arbitrary `Object payload` fields.

Prefer an envelope shape based on:

- `byte[] payload`;
- `String contentType`;
- `String schemaVersion`;
- optional `partitionKey`.

Serialization belongs to the mapper, not to the transport implementation.

## Important semantic rule

If an external transport reaction is plugged into the current core event runtime, forwarding remains best-effort.

It still does not imply durable storage, retry, replay, exactly-once or guaranteed delivery.

## Durable delivery remains separate

Durable delivery needs explicit design around:

- storage;
- retries;
- dead-letter handling;
- idempotency;
- transactional boundaries;
- operational observability.

That should be handled when a real use case appears.
