package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.event.Event;

public interface EventBus extends Runnable {
    void stopBus() throws InterruptedException;

    void acceptEvent(Event event);
}