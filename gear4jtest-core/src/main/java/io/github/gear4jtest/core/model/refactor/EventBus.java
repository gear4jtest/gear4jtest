package io.github.gear4jtest.core.model.refactor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;

public class EventBus {
//    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
//
//    public void register(EventListener listener) { listeners.add(listener); }
//    public void unregister(EventListener listener) { listeners.remove(listener); }
//
//    public CompletableFuture<Void> publish(Event event) {
//        return CompletableFuture.runAsync(() ->
//            listeners.forEach(l -> l.handle(event))
//        );
//    }
}