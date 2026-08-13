package io.github.gear4jtest.core.api;

import java.util.Map;
import java.util.Optional;

final class ImmutableStationMetadata implements StationMetadata {
    static final ImmutableStationMetadata EMPTY = new ImmutableStationMetadata(Map.of());

    private final Map<Class<?>, Object> values;

    ImmutableStationMetadata(Map<Class<?>, Object> values) {
        this.values = values;
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(values.get(type)));
    }
}
