package io.github.gear4jtest.xml.expression;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts approved input data to an immutable GEL value tree. */
public final class GearExpressionValues {
    public static final int DEFAULT_MAX_DEPTH = 64;
    public static final int DEFAULT_MAX_NODES = 10_000;

    private GearExpressionValues() {
    }

    public static Object snapshot(Object value) {
        return snapshot(value, PropertyAccessPolicy.secureDefaults());
    }

    public static Object snapshot(Object value, PropertyAccessPolicy propertyAccessPolicy) {
        Objects.requireNonNull(propertyAccessPolicy, "propertyAccessPolicy");
        SnapshotState state = new SnapshotState(propertyAccessPolicy);
        return state.snapshot(value, 0);
    }

    static Object snapshotMaps(Object value) {
        return new MapSnapshotState().snapshot(value, 0);
    }

    static boolean isSafeScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof URI
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value.getClass() == BigInteger.class
                || value.getClass() == BigDecimal.class
                || value.getClass().getPackageName().equals("java.time")
                || value.getClass().getPackageName().startsWith("java.time.");
    }

    private static final class SnapshotState {
        private final PropertyAccessPolicy propertyAccessPolicy;
        private final IdentityHashMap<Object, Boolean> activeObjects = new IdentityHashMap<>();
        private int nodes;

        SnapshotState(PropertyAccessPolicy propertyAccessPolicy) {
            this.propertyAccessPolicy = propertyAccessPolicy;
        }

        Object snapshot(Object value, int depth) {
            if (value == null || isSafeScalar(value)) {
                countNode();
                return value;
            }
            if (depth >= DEFAULT_MAX_DEPTH) {
                throw new GearExpressionException("GEL value snapshot exceeds max depth " + DEFAULT_MAX_DEPTH);
            }
            countNode();
            if (activeObjects.put(value, Boolean.TRUE) != null) {
                throw new GearExpressionException(
                        "GEL value snapshot contains a cycle at " + value.getClass().getName());
            }
            try {
                if (value instanceof Map<?, ?> map) {
                    return snapshotMap(map, depth);
                }
                if (value instanceof Iterable<?> iterable) {
                    return snapshotIterable(iterable, depth);
                }
                if (value.getClass().isArray()) {
                    return snapshotArray(value, depth);
                }
                if (value.getClass().isRecord()) {
                    return snapshotRecord(value, depth);
                }
                throw new GearExpressionException("Unsupported GEL snapshot type: " + value.getClass().getName()
                        + "; convert it to a map or explicitly snapshot an allowlisted record");
            } finally {
                activeObjects.remove(value);
            }
        }

        private Map<String, Object> snapshotMap(Map<?, ?> source, int depth) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (!(key instanceof String stringKey)) {
                    throw new GearExpressionException("GEL snapshot map keys must be strings");
                }
                copy.put(stringKey, snapshot(value, depth + 1));
            });
            return new InertValueMap(copy);
        }

        private List<Object> snapshotIterable(Iterable<?> source, int depth) {
            List<Object> copy = new ArrayList<>();
            source.forEach(value -> copy.add(snapshot(value, depth + 1)));
            return Collections.unmodifiableList(copy);
        }

        private List<Object> snapshotArray(Object source, int depth) {
            int length = Array.getLength(source);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                copy.add(snapshot(Array.get(source, i), depth + 1));
            }
            return Collections.unmodifiableList(copy);
        }

        private Map<String, Object> snapshotRecord(Object source, int depth) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (RecordComponent component : source.getClass().getRecordComponents()) {
                Object componentValue = propertyAccessPolicy.readProperty(source, component.getName());
                copy.put(component.getName(), snapshot(componentValue, depth + 1));
            }
            return new InertValueMap(copy);
        }

        private void countNode() {
            nodes++;
            if (nodes > DEFAULT_MAX_NODES) {
                throw new GearExpressionException("GEL value snapshot exceeds max node count " + DEFAULT_MAX_NODES);
            }
        }
    }

    private static final class MapSnapshotState {
        private final IdentityHashMap<Object, Boolean> activeMaps = new IdentityHashMap<>();
        private int nodes;

        Object snapshot(Object value, int depth) {
            countNode();
            if (!(value instanceof Map<?, ?> map)) {
                return value;
            }
            if (depth >= DEFAULT_MAX_DEPTH) {
                throw new GearExpressionException("GEL map snapshot exceeds max depth " + DEFAULT_MAX_DEPTH);
            }
            if (activeMaps.put(value, Boolean.TRUE) != null) {
                throw new GearExpressionException("GEL map snapshot contains a cycle");
            }
            try {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, child) -> {
                    if (!(key instanceof String stringKey)) {
                        throw new GearExpressionException("GEL context map keys must be strings");
                    }
                    copy.put(stringKey, snapshot(child, depth + 1));
                });
                return new InertValueMap(copy);
            } finally {
                activeMaps.remove(value);
            }
        }

        private void countNode() {
            nodes++;
            if (nodes > DEFAULT_MAX_NODES) {
                throw new GearExpressionException("GEL map snapshot exceeds max node count " + DEFAULT_MAX_NODES);
            }
        }
    }
}
