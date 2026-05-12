package io.github.gear4jtest.core.event;

public interface EventBus extends Runnable {
    void stopBus() throws InterruptedException;

    void acceptEvent(Event event);
}
