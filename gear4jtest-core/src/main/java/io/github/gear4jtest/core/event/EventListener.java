package io.github.gear4jtest.core.event;

@FunctionalInterface
public interface EventListener<T extends Event> {

	void handleEvent(T e);
	
}
