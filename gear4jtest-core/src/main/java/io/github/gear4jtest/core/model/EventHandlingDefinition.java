package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.EventBuss;

public class EventHandlingDefinition {

	private List<EventBuss> eventBuses;
	private EventConfiguration globalEventConfiguration;

	public EventHandlingDefinition() {
		this.eventBuses = new ArrayList<>();
	}

	public List<EventBuss> getEventBuses() {
		return eventBuses;
	}

	public EventConfiguration getGlobalEventConfiguration() {
		return globalEventConfiguration;
	}

	public static class Builder {

		private final EventHandlingDefinition managedInstance;

		Builder() {
			managedInstance = new EventHandlingDefinition();
		}

		public Builder bus(EventBuss eventBus) {
			if (managedInstance.eventBuses == null) {
				managedInstance.eventBuses = new ArrayList<>();
			}
			managedInstance.eventBuses.add(eventBus);
			return this;
		}

		public Builder globalEventConfiguration(EventConfiguration eventConfiguration) {
			managedInstance.globalEventConfiguration = eventConfiguration;
			return this;
		}

		public EventHandlingDefinition build() {
			return managedInstance;
		}

	}

	public static class EventConfiguration {

		private boolean eventOnParameterChanged;

		public boolean isEventOnParameterChanged() {
			return eventOnParameterChanged;
		}

		public static class Builder {

			private final EventConfiguration managedInstance;

			Builder() {
				managedInstance = new EventConfiguration();
			}

			public Builder eventOnParameterChanged(boolean eventOnParameterChanged) {
				managedInstance.eventOnParameterChanged = eventOnParameterChanged;
				return this;
			}

			public EventConfiguration build() {
				return managedInstance;
			}

		}

	}
}
