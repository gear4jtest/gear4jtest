package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.annotation.Internal;

/**
 * Run-scoped registry used to cache resources per station within a single
 * pipeline execution.
 *
 * <p>
 * This is intentionally separate from {@link ExecutionContext}: it is a
 * technical runtime service, not user-facing execution state.
 * </p>
 */
public final class StationScopedResourceRegistry {
    private final Map<Object, Object> resources = new ConcurrentHashMap<>();

    public <T> T getOrCreate(String stationId, Class<T> type, Supplier<T> factory) {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");

        ResourceKey key = new ResourceKey(stationId, type);
        Object value = resources.computeIfAbsent(key, ignored -> factory.get());
        return type.cast(value);
    }

    @Internal
    public <T> T getOrCreate(Object stationIdentity,
                             String stationId,
                             Class<T> type,
                             Supplier<T> factory) {
        Objects.requireNonNull(stationIdentity, "stationIdentity");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");

        IdentityResourceKey key = new IdentityResourceKey(stationIdentity, stationId, type);
        Object value = resources.computeIfAbsent(key, ignored -> factory.get());
        return type.cast(value);
    }

    public void clear(String stationId, Class<?> type) {
        if (stationId == null || type == null) {
            return;
        }
        resources.remove(new ResourceKey(stationId, type));
        resources.keySet().removeIf(key -> key instanceof IdentityResourceKey identityKey
                && identityKey.matches(stationId, type));
    }

    public void clearAll() {
        resources.clear();
    }

    private record ResourceKey(String stationId, Class<?> type) {
        private ResourceKey {
            Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(type, "type");
        }
    }

    private static final class IdentityResourceKey {
        private final Object stationIdentity;
        private final String stationId;
        private final Class<?> type;

        private IdentityResourceKey(Object stationIdentity, String stationId, Class<?> type) {
            this.stationIdentity = stationIdentity;
            this.stationId = stationId;
            this.type = type;
        }

        private boolean matches(String expectedStationId, Class<?> expectedType) {
            return stationId.equals(expectedStationId) && type.equals(expectedType);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof IdentityResourceKey key
                    && stationIdentity == key.stationIdentity
                    && type.equals(key.type);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(stationIdentity) + type.hashCode();
        }
    }
}
