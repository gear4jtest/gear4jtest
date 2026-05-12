package io.github.gear4jtest.core.api.context;

/**
 * Unsafe {@link PayloadCloner} implementation that always returns the original
 * payload reference.
 */
public final class NoOpPayloadCloner implements PayloadCloner {

    @Override
    public <T> T clonePayload(T payload) {
        return payload;
    }
}
