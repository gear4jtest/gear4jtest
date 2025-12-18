package io.github.gear4jtest.core.model;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.github.gear4jtest.core.model.WorkerParamsInjector.*;

public final class WorkerIntrospector {

    private static final ConcurrentMap<Class<?>, Boolean> STATEFUL_CACHE = new ConcurrentHashMap<>();

    private WorkerIntrospector() {
        // utility
    }

    public static boolean isStateful(Object transformer) {
        Objects.requireNonNull(transformer, "transformer must not be null");

        if (transformer instanceof ConcurrencyAwareTransformer aware) {
            return switch (aware.statefulness()) {
                case STATEFUL -> true;
                case STATELESS -> false;
                case AUTO -> detectStateful(transformer.getClass());
            };
        }

        return detectStateful(transformer.getClass());
    }

    private static boolean detectStateful(Class<?> transformerClass) {
        return STATEFUL_CACHE.computeIfAbsent(transformerClass, WorkerIntrospector::scanForParameters);
    }

    private static boolean scanForParameters(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Parameter.class.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}