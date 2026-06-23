package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
    private final Map<ResourceKey, Object> resources = new ConcurrentHashMap<>();

    public <T> T getOrCreate(String stationId, Class<T> type, Supplier<T> factory) {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");

        ResourceKey key = new ResourceKey(stationId, type);
        Object value = resources.computeIfAbsent(key, ignored -> factory.get());
        return type.cast(value);
    }

    public void clear(String stationId, Class<?> type) {
        if (stationId == null || type == null) {
            return;
        }
        resources.remove(new ResourceKey(stationId, type));
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
}
