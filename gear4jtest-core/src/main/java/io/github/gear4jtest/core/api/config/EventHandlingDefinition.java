package io.github.gear4jtest.core.api.config;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.event.EventBus;

public class EventHandlingDefinition {

	private final List<EventBus> eventBuses;
	private final EventConfiguration globalEventConfiguration;

	private EventHandlingDefinition(List<EventBus> eventBuses, EventConfiguration globalEventConfiguration) {
		this.eventBuses = eventBuses != null ? List.copyOf(eventBuses) : List.of();
		this.globalEventConfiguration = globalEventConfiguration;
	}

	public List<EventBus> getEventBuses() {
		return eventBuses;
	}

	public EventConfiguration getGlobalEventConfiguration() {
		return globalEventConfiguration;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final List<EventBus> eventBuses = new ArrayList<>();
		private EventConfiguration globalEventConfiguration;

		public Builder bus(EventBus eventBus) {
			if (eventBus != null) {
				this.eventBuses.add(eventBus);
			}
			return this;
		}

		public Builder globalEventConfiguration(EventConfiguration eventConfiguration) {
			this.globalEventConfiguration = eventConfiguration;
			return this;
		}

		public EventHandlingDefinition build() {
			return new EventHandlingDefinition(eventBuses, globalEventConfiguration);
		}
	}

	public static class EventConfiguration {

		private final boolean eventOnParameterChanged;

		private EventConfiguration(boolean eventOnParameterChanged) {
			this.eventOnParameterChanged = eventOnParameterChanged;
		}

		public boolean isEventOnParameterChanged() {
			return eventOnParameterChanged;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private boolean eventOnParameterChanged;

			public Builder eventOnParameterChanged(boolean eventOnParameterChanged) {
				this.eventOnParameterChanged = eventOnParameterChanged;
				return this;
			}

			public EventConfiguration build() {
				return new EventConfiguration(eventOnParameterChanged);
			}
		}
	}
}
