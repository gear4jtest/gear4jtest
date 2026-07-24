package io.github.gear4jtest.core.persistence;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.exception.PayloadCloneException;

final class PersistenceSnapshots {
    private PersistenceSnapshots() {
    }

    @SuppressWarnings("unchecked")
    static <T> T capture(T value, PayloadCloner payloadCloner) {
        PayloadCloner effectiveCloner = Objects.requireNonNull(payloadCloner, "payloadCloner must not be null");
        return (T) captureValue(value, effectiveCloner, new IdentityHashMap<>());
    }

    private static Object captureValue(Object value,
                                       PayloadCloner payloadCloner,
                                       IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return captureMap(map, payloadCloner, visiting);
        }
        if (value instanceof List<?> list) {
            return captureList(list, payloadCloner, visiting);
        }
        if (value instanceof Set<?> set) {
            return captureSet(set, payloadCloner, visiting);
        }
        if (value instanceof Collection<?> collection) {
            return captureCollection(collection, payloadCloner, visiting);
        }
        if (value instanceof Optional<?> optional) {
            return captureOptional(optional, payloadCloner, visiting);
        }
        if (value.getClass().isArray()) {
            return captureArray(value, payloadCloner, visiting);
        }
        try {
            return payloadCloner.clonePayload(value);
        } catch (PayloadCloneException exception) {
            throw new PayloadCloneException("Could not isolate persistence value of type "
                    + value.getClass().getName() + ". Configure a PayloadCloner on the persistence manager builder "
                    + "or retain only immutable values after redaction.", exception);
        }
    }

    private static Map<?, ?> captureMap(Map<?, ?> source,
                                        PayloadCloner payloadCloner,
                                        IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Map<Object, Object> snapshot = new LinkedHashMap<>();
            source.forEach((key, value) -> snapshot.put(captureValue(key, payloadCloner, visiting),
                                                        captureValue(value, payloadCloner, visiting)));
            return Collections.unmodifiableMap(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static List<?> captureList(List<?> source,
                                       PayloadCloner payloadCloner,
                                       IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> snapshot = new ArrayList<>(source.size());
            source.forEach(value -> snapshot.add(captureValue(value, payloadCloner, visiting)));
            return Collections.unmodifiableList(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static Set<?> captureSet(Set<?> source,
                                     PayloadCloner payloadCloner,
                                     IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Set<Object> snapshot = new LinkedHashSet<>();
            source.forEach(value -> snapshot.add(captureValue(value, payloadCloner, visiting)));
            return Collections.unmodifiableSet(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static Collection<?> captureCollection(Collection<?> source,
                                                   PayloadCloner payloadCloner,
                                                   IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> snapshot = new ArrayList<>(source.size());
            source.forEach(value -> snapshot.add(captureValue(value, payloadCloner, visiting)));
            return Collections.unmodifiableCollection(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static Optional<?> captureOptional(Optional<?> source,
                                               PayloadCloner payloadCloner,
                                               IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            return source.map(value -> captureValue(value, payloadCloner, visiting));
        } finally {
            visiting.remove(source);
        }
    }

    private static Object captureArray(Object source,
                                       PayloadCloner payloadCloner,
                                       IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            int length = Array.getLength(source);
            Object snapshot = Array.newInstance(source.getClass().getComponentType(), length);
            for (int index = 0; index < length; index++) {
                Array.set(snapshot, index, captureValue(Array.get(source, index), payloadCloner, visiting));
            }
            return snapshot;
        } finally {
            visiting.remove(source);
        }
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new PayloadCloneException("Cannot create a persistence snapshot from a cyclic "
                    + value.getClass().getName() + "; configure a PayloadCloner that isolates the cycle before "
                    + "persistence");
        }
    }
}
