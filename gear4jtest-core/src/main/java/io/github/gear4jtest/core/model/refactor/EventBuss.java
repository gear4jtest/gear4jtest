package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.event.Event;

public interface EventBuss extends Runnable {
    void acceptEvent(Event event);
}