package io.github.gear4jtest.core.event;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;

@FunctionalInterface
public interface EventListener<T extends Event> {
    void handleEvent(T e);

    default boolean isAcceptable(Object item) {
        return Arrays.stream(getClass().getGenericInterfaces()).map(ParameterizedType.class::cast)
                .filter(type -> type.getRawType().equals(EventListener.class))
                .map(ParameterizedType::getActualTypeArguments).findFirst().map(types -> types[0])
                .map(Class.class::cast).map(acceptableType -> acceptableType.isInstance(item)).orElse(false);
    }
}
