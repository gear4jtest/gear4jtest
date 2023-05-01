package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class EventQueue {

	private final LinkedBlockingQueue<Event> eventQueue;
	private final List<EventListener> eventListeners;
	private final List<EventQueueFilter> filters;

	public EventQueue(List<EventListener> eventListeners, List<EventQueueFilter> filters) {
		this.eventQueue = new LinkedBlockingQueue<>();
		this.eventListeners = eventListeners;
		this.filters = filters;
	}

	public void run() {
		try {
			Event event = null;
			while ((event = eventQueue.take()) != null) {
				// check rules before processing event
				for (EventListener eventListener : eventListeners) {
					eventListener.handleEvent(event);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void pushEvent(Event e) {
		if (filters.stream().allMatch(filter -> filter.isEligible(e))) {
			eventQueue.add(e);
		}
	}

}
