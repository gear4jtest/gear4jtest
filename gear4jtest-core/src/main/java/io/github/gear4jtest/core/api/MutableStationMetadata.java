package io.github.gear4jtest.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MutableStationMetadata implements StationMetadata {
    private final Map<Class<?>, Object> values = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) values.get(type));
    }

    public <T> MutableStationMetadata put(Class<T> type, T value) {
        values.put(type, value);
        return this;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public StationMetadata immutableCopy() {
        return StationMetadata.copyOf(values);
    }
}
