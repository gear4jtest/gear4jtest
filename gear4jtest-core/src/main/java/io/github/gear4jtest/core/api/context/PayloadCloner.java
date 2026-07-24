package io.github.gear4jtest.core.api.context;

/**
 * Strategy interface used to isolate payload instances before they cross an
 * asynchronous or concurrently executed boundary, including container branches
 * and persistence buffers.
 *
 * <p>
 * Implementations may return the same instance only when the provided payload
 * type is known to be immutable and therefore safe to share. Mutable payloads
 * must be deeply cloned.
 * </p>
 */
public interface PayloadCloner {
    /**
     * Returns a payload instance that is safe to use in an isolated branch
     * execution or persistence snapshot.
     *
     * @param payload the payload to isolate
     * @param <T>     the payload type
     * @return a safely isolated payload instance
     */
    <T> T clonePayload(T payload);
}
