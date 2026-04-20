package io.github.gear4jtest.core.event.transport;

/**
 * Publishes a serialized event envelope to an external transport such as Kafka, SQS or RabbitMQ.
 *
 * <p>This abstraction does not imply durability by itself. When used through the current in-memory
 * Gear4J event runtime, publication remains best-effort.</p>
 */
public interface ExternalEventTransport {

    PublishResult publish(EventEnvelope envelope) throws Exception;
}
