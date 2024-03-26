package io.github.gear4jtest.core.event;

@FunctionalInterface
public interface EventQueueFilter {

	boolean isEligible(Event e);
	
}
