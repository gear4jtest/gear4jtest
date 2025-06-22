package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.event.EventBusFilter;

public class SimpleEventBuss implements EventBuss {

	private final String id;
	private final LinkedBlockingQueue<Event> eventQueue;
	private final List<EventBusFilter> filters;
	private final List<EventListener<?>> eventListeners;

    public SimpleEventBuss(String id, List<EventBusFilter> filters, List<EventListener<?>> eventListeners) {
		this.id = id;
		this.eventQueue = new LinkedBlockingQueue<>();
        this.filters = filters;
		this.eventListeners = eventListeners;
    }

    @Override
    public void run() {
		try {
			Event event;
			while ((event = eventQueue.take()) != null) {
				for (EventListener eventListener : eventListeners) {
					if (eventListener.isAcceptable(event)) {
						eventListener.handleEvent(event);
					}
				}

				if (eventQueue.isEmpty()) {
					synchronized(this) {
						this.notify();
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
    }

	@Override
	public void acceptEvent(Event event) {
		if (filters == null || filters.stream().allMatch(filter -> filter.isEligible(event))) {
			eventQueue.add(event);
		}
	}

	public static class Builder {

		private String id;
		private List<EventBusFilter> filters;
		private List<EventListener<?>> eventListeners;

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder filter(EventBusFilter filter) {
			if (this.filters == null) {
				this.filters = new ArrayList<>();
			}
			this.filters.add(filter);
			return this;
		}

		public Builder eventListener(EventListener<?> eventListener) {
			if (this.eventListeners == null) {
				this.eventListeners = new ArrayList<>();
			}
			this.eventListeners.add(eventListener);
			return this;
		}

		public SimpleEventBuss build() {
			return new SimpleEventBuss(id, filters, eventListeners);
		}
	}
}
