package io.github.gear4jtest.core.api.context;

/**
 * Strategy interface used by the engine to isolate payload instances before they are
 * dispatched to container branches.
 *
 * <p>Implementations may return the same instance only when the provided payload type is
 * known to be immutable and therefore safe to share. Mutable payloads must be deeply cloned.
 */
public interface PayloadCloner {

    /**
     * Returns a payload instance that is safe to use in an isolated branch execution.
     *
     * @param payload the payload to isolate
     * @param <T> the payload type
     * @return a safely isolated payload instance
     */
    <T> T clonePayload(T payload);
}
