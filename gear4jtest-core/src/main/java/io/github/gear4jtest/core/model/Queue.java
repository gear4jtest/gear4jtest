package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.event.EventQueueFilter;

public class Queue {

	private String name;
	private List<EventListener> eventListeners;
	private List<EventQueueFilter> filters;
	private boolean parallelExecution;
	private Integer initialThreadNumber;
	private Integer maxThreadNumber;

	public Queue() {
		this.eventListeners = new ArrayList<>();
	}

	public String getName() {
		return name;
	}

	public List<EventListener> getEventListeners() {
		return eventListeners;
	}

	public List<EventQueueFilter> getFilters() {
		return filters;
	}

	public boolean isParallelExecution() {
		return parallelExecution;
	}

	public Integer getInitialThreadNumber() {
		return initialThreadNumber;
	}

	public Integer getMaxThreadNumber() {
		return maxThreadNumber;
	}

	public static class Builder {

		private final Queue managedInstance;

		Builder() {
			managedInstance = new Queue();
		}

		public Builder name(String name) {
			managedInstance.name = name;
			return this;
		}

		public Builder eventListener(EventListener eventListener) {
			managedInstance.eventListeners.add(eventListener);
			return this;
		}

		public Builder filter(EventQueueFilter filter) {
			if (managedInstance.filters == null) {
				managedInstance.filters = new ArrayList<>();
			}
			managedInstance.filters.add(filter);
			return this;
		}

		public Builder parallelExecution(boolean parallelExecution) {
			managedInstance.parallelExecution = parallelExecution;
			return this;
		}

		public Builder initialThreadNumber(Integer initialThreadNumber) {
			managedInstance.initialThreadNumber = initialThreadNumber;
			return this;
		}

		public Builder maxThreadNumber(Integer maxThreadNumber) {
			managedInstance.maxThreadNumber = maxThreadNumber;
			return this;
		}
		
		public Queue build() {
			return this.managedInstance;
		}

	}
}
