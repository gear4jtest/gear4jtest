package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class EventHandlingDefinition {

	private List<Queue> queues;
	private EventConfiguration globalEventConfiguration;

	public EventHandlingDefinition() {
		this.queues = new ArrayList<>();
	}

	public List<Queue> getQueues() {
		return queues;
	}

	public EventConfiguration getGlobalEventConfiguration() {
		return globalEventConfiguration;
	}

	public static class Builder {

		private final EventHandlingDefinition managedInstance;

		Builder() {
			managedInstance = new EventHandlingDefinition();
		}

		public Builder queue(Queue queue) {
			if (managedInstance.queues == null) {
				managedInstance.queues = new ArrayList<>();
			}
			managedInstance.queues.add(queue);
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
