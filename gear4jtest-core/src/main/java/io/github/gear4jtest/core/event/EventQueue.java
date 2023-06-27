package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.event.EventQueueInitializer.ParallelConfiguration;

public class EventQueue implements Runnable {

	private final String name;
	private final LinkedBlockingQueue<Event> eventQueue;
	private final List<EventListener> eventListeners;
	private final List<EventQueueFilter> filters;
	private final ThreadPoolExecutor executor;

	public EventQueue(String name, List<EventListener> eventListeners, List<EventQueueFilter> filters,
			ParallelConfiguration parallelConfiguration) {
		this.name = name;
		this.eventQueue = new LinkedBlockingQueue<>();
		this.eventListeners = eventListeners;
		this.filters = filters;
		if (parallelConfiguration.isParallelExecution()) {
			this.executor = new ThreadPoolExecutor(parallelConfiguration.getInitialThreadNumber(),
					parallelConfiguration.getMaxThreadNumber(), 0L, TimeUnit.MILLISECONDS,
					new LinkedBlockingQueue<Runnable>());
		} else {
			this.executor = null;
		}
	}

	@Override
	public void run() {
		try {
			Event event = null;
			while ((event = eventQueue.take()) != null) {
				// check rules before processing event
				final Event evt = event;
				for (EventListener eventListener : eventListeners) {
					if (executor != null) {
						executor.submit(() -> eventListener.handleEvent(evt));
					} else {
						eventListener.handleEvent(evt);
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void pushEvent(Event e) {
		if (filters == null || filters.stream().allMatch(filter -> filter.isEligible(e))) {
			eventQueue.add(e);
		}
	}

}
