package io.github.gear4jtest.core.event;

/**
 * Run-scoped capability for publishing best-effort in-memory events.
 *
 * <p>
 * Publication does not imply durable delivery, replay or exactly-once
 * processing. Consumers that need those guarantees must use a durable event
 * subsystem.
 * </p>
 */
public interface EventPublisher {
    <T extends Event> void publish(T event);
}
