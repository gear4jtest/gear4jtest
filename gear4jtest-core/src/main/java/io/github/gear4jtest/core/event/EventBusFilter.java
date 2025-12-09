package io.github.gear4jtest.core.event;

@FunctionalInterface
public interface EventBusFilter {

	boolean isEligible(Event e);
	
}
