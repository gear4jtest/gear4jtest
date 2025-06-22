package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.persistence.DatabasePipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

import static io.github.gear4jtest.core.model.refactor.OperationExecution.*;

public class AssemblyLineDefinition<IN, OUT> {

	private String id;
	private String description;
	private List<OperationDefinition<?, ?>> operations;
	private Configuration configuration;

	private AssemblyLineDefinition() {
	}

	public ExecutionResult<OUT> execute(IN input, Map<String, Object> context, ResourceFactory resourceFactory) {
		EventManager eventManager = null;
		try {
			eventManager = new EventManager(configuration != null ? configuration.getEventHandlingDefinition().getEventBuses() : null);
			ExecutionContext executionContext = new ExecutionContext(id, eventManager, resourceFactory);
			ExecutionReport report = new ExecutionReport();
			PipelineExecution execution = new PipelineExecution(executionContext.getExecutionId(), id, new HashMap<>(context));
			saveExecution(execution);
			Object current = input;

			for (OperationDefinition<?, ?> op : operations) {
				var a = (OperationDefinition<Object, Object>) op;
				OperationResult<Object> result = a.run(current, executionContext);
				report.addOperationReport(result.getReport());

				if (!result.isSuccess() || result.getReport().getStatus() == OperationReport.Status.FAILED || result.getReport().getStatus() == OperationReport.Status.STOPPED) {
					report.complete();
					return new ExecutionResult<>(executionContext.getExecutionId(), null, false, result.getError(), report);
				}
				current = result.getResult();
			}

			report.complete();
			saveExecution(execution, executionContext, report, current);
			return new ExecutionResult<>(executionContext.getExecutionId(), (OUT) current, true, null, report);
		} finally {
			if (eventManager != null) {
				eventManager.shutdown();
			}
		}
	}

	private void saveExecution(PipelineExecution execution) {
		if (Optional.ofNullable(configuration).map(Configuration::getPersistence).isPresent()) {
			switch (configuration.persistence.getPersistenceType()) {
				case IN_MEMORY -> InMemoryPipelineExecutionRepository.INSTANCE.save(execution);
				case DATABASE -> {
					var repo = new DatabasePipelineExecutionRepository(configuration.persistence.getDataSource());
					repo.initialize();
					repo.save(execution);
				}
				default -> throw new UnsupportedOperationException("Unsupported persistence type: " + configuration.persistence.getPersistenceType());
			}
		}
	}

	private void saveExecution(PipelineExecution execution, ExecutionContext executionContext, ExecutionReport report, Object result) {
		if (Optional.ofNullable(configuration).map(Configuration::getPersistence).isEmpty()) {
			return;
		}

		if (report.isFatal()) {
			execution.setStatus(ExecutionStatus.FAILED);
		} else if (report.isShouldStop()) {
			execution.setStatus(ExecutionStatus.STOPPED);
		} else {
			execution.setStatus(ExecutionStatus.SUCCEEDED);
		}
		execution.setContext(executionContext.getContext());
		execution.setEndTime(Instant.now());
		if (configuration.persistence.isStoreResultObject()) {
			execution.setResult(result);
		}
		execution.setOperations(buildOperationRecords(report, executionContext.getExecutionId(), null));

		switch (configuration.persistence.getPersistenceType()) {
			case IN_MEMORY -> InMemoryPipelineExecutionRepository.INSTANCE.update(execution);
			case DATABASE -> new DatabasePipelineExecutionRepository(configuration.persistence.getDataSource()).update(execution);
			default -> throw new UnsupportedOperationException("Unsupported persistence type: " + configuration.persistence.getPersistenceType());
		}
	}

	private List<OperationExecutionRecord> buildOperationRecords(ExecutionReport report, UUID pipelineExecutionId, UUID parentExecutionId) {
		return report.getOperations().stream()
				.map(entry -> buildOperationRecords(entry, pipelineExecutionId, parentExecutionId))
				.toList();
	}

	private OperationExecutionRecord buildOperationRecords(OperationReport report, UUID pipelineExecutionId, UUID parentExecutionId) {
		List<OperationExecutionRecord> children = new ArrayList<>();
		if (report.getSubOperationReports() != null && !report.getSubOperationReports().isEmpty()) {
			children = report.getSubOperationReports().stream().map(subEntry -> buildOperationRecords(subEntry, pipelineExecutionId, report.getId())).toList();
		}
		return new OperationExecutionRecord(UUID.randomUUID().toString(),
				pipelineExecutionId.toString(),
				report.getOperationId(),
				Optional.ofNullable(parentExecutionId).map(UUID::toString).orElse(null),
				report.getStatus(),
				report.getStartTime(),
				report.getEndTime(),
				report.getError() != null ? report.getError().getMessage() : null,
				report.getErrorHandlerExceptions() != null && !report.getErrorHandlerExceptions().isEmpty() ? report.getErrorHandlerExceptions().stream().map(Exception::getMessage).collect(Collectors.joining(", ")) : null,
				report.getContext(),
				children);
	}

	public String getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public static class Builder<IN, OUT> {

		private final AssemblyLineDefinition<IN, OUT> managedInstance;

		public Builder(String identifier) {
			managedInstance = new AssemblyLineDefinition<>();
			managedInstance.operations = new ArrayList<>();
			managedInstance.id = identifier;
		}

		public <T> Builder<IN, T> then(OperationDefinition<OUT, T> operation) {
			managedInstance.operations.add(operation);
			return (Builder<IN, T>) this;
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
		private PersistenceConfiguration persistence;
		
		public OperationConfigurationDefinition getOperationDefaultConfiguration() {
			return operationDefaultConfiguration;
		}

		public EventHandlingDefinition getEventHandlingDefinition() {
			return eventHandlingDefinition;
		}

		public PersistenceConfiguration getPersistence() {
			return persistence;
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

			public Builder persistence(PersistenceConfiguration persistence) {
				this.managedInstance.persistence = persistence;
				return this;
			}

			public Configuration build() {
				return managedInstance;
			}

		}
	}

}
