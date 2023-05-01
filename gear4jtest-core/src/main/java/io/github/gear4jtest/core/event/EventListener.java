package io.github.gear4jtest.core.event;

@FunctionalInterface
public interface EventListener {

	void handleEvent(Event e);
	
}
