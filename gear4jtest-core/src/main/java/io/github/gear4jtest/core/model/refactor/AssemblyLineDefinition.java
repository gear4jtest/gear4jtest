package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.model.EventHandlingDefinition;

public class AssemblyLineDefinition<IN, OUT> implements Whatever<IN, OUT> {

	private String id;
	private String description;

	private LineDefinition<IN, OUT> lineDefinition;

	private Configuration configuration;

	public AssemblyLineDefinition() {
	}

	public String getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public LineDefinition<IN, OUT> getLineDefinition() {
		return lineDefinition;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public static class Builder<IN, OUT> {

		private final AssemblyLineDefinition<IN, OUT> managedInstance;

		public Builder(String identifier) {
			managedInstance = new AssemblyLineDefinition<>();
			managedInstance.id = identifier;
		}

		public Builder(String identifier, LineDefinition<IN, OUT> line) {
			this(identifier);
			managedInstance.lineDefinition = line;
		}

		public Builder(Builder<?, ?> builder, LineDefinition<IN, OUT> line) {
			this(builder.managedInstance.id, line);
		}

		public <START, END> Builder<START, END> definition(LineDefinition<START, END> line) {
			return new Builder<>(this, line);
		}

		public Builder<IN, OUT> configuration(Configuration configuration) {
			this.managedInstance.configuration = configuration;
			return this;
		}

		public AssemblyLineDefinition<IN, OUT> build() {
			return managedInstance;
		}

	}

	public static class Configuration {
		private OperationConfigurationDefinition operationDefaultConfiguration;
		private EventHandlingDefinition eventHandlingDefinition;
		
		public OperationConfigurationDefinition getOperationDefaultConfiguration() {
			return operationDefaultConfiguration;
		}

		public EventHandlingDefinition getEventHandlingDefinition() {
			return eventHandlingDefinition;
		}

		public static class Builder {

			private final Configuration managedInstance;

			public Builder() {
				managedInstance = new Configuration();
			}

			public Builder stepDefaultConfiguration(OperationConfigurationDefinition operationDefaultConfiguration) {
				this.managedInstance.operationDefaultConfiguration = operationDefaultConfiguration;
				return this;
			}

			public Builder eventHandlingDefinition(EventHandlingDefinition eventHandlingDefinition) {
				this.managedInstance.eventHandlingDefinition = eventHandlingDefinition;
				return this;
			}

			public Configuration build() {
				return managedInstance;
			}

		}
	}

}
