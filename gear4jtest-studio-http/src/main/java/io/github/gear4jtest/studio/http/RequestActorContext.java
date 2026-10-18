package io.github.gear4jtest.studio.http;

import java.util.Objects;

import io.github.gear4jtest.studio.management.CurrentActorProvider;
import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Local HTTP bridge. Production hosts may instead supply their own framework
 * identity provider.
 */
public final class RequestActorContext implements CurrentActorProvider {
    private final ThreadLocal<StudioIdentity> current = new ThreadLocal<>();

    @Override
    public StudioIdentity currentActor() {
        return current.get();
    }

    public Scope bind(StudioIdentity identity) {
        if (current.get() != null)
            throw new IllegalStateException("Actor already bound");
        current.set(Objects.requireNonNull(identity));
        return current::remove;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
