package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
@FunctionalInterface
public interface EventReaction<T extends Event> {
    void handle(T event) throws Exception;
}
