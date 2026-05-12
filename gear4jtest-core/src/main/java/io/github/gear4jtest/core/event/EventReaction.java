package io.github.gear4jtest.core.event;

@FunctionalInterface
public interface EventReaction<T extends Event> {
    void handle(T event) throws Exception;
}
