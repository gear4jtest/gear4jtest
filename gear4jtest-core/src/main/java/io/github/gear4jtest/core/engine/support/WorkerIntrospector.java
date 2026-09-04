package io.github.gear4jtest.core.engine.support;

import java.lang.reflect.Field;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.StationParameter;

public final class WorkerIntrospector {
    private static final ClassValue<Boolean> STATEFUL_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return scanForParameters(type);
        }
    };

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
        return STATEFUL_CACHE.get(transformerClass);
    }

    private static boolean scanForParameters(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (StationParameter.class.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
