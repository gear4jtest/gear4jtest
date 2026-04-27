package io.github.gear4jtest.core.api.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Run-scoped registry used to cache resources per station within a single pipeline execution.
 *
 * <p>This is intentionally separate from {@link ExecutionContext}: it is a technical runtime service,
 * not user-facing execution state.</p>
 */
public final class StationScopedResourceRegistry {

    private final Map<String, Object> resources = new ConcurrentHashMap<>();

    public <T> T getOrCreate(String stationId, Class<T> type, Supplier<T> factory) {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");

        String key = key(stationId, type);
        Object value = resources.computeIfAbsent(key, ignored -> factory.get());
        return type.cast(value);
    }

    public void clear(String stationId, Class<?> type) {
        if (stationId == null || type == null) {
            return;
        }
        resources.remove(key(stationId, type));
    }

    public void clearAll() {
        resources.clear();
    }

    private static String key(String stationId, Class<?> type) {
        return stationId + ":" + type.getName();
    }
}
