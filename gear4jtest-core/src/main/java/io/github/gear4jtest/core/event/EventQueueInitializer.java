package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.context.AssemblyLineExecution;
import io.github.gear4jtest.core.internal.Initializer;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.Queue;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration;

public class EventQueueInitializer implements Initializer {
	
//	private final List<Queue> queues;
//
//	public EventQueueInitializer(EventHandling eventHandling) {
//		this.queues = eventHandling.getQueues();
//	}

	@Override
	public void initialize(AssemblyLineDefinition assemblyLine, AssemblyLineExecution execution) {
		Optional<List<Queue>> queuesOpt = Optional.ofNullable(assemblyLine.getConfiguration())
				.map(Configuration::getEventHandlingDefinition)
				.map(EventHandlingDefinition::getQueues);
		if (!queuesOpt.isPresent()) {
			return;
		}
		List<Queue> queues = queuesOpt.get();
		List<EventQueue> eventQueues = queues.stream().map(EventQueueInitializer::buildQueue).collect(Collectors.toList());
		ExecutorService service = Executors.newFixedThreadPool(queues.size());
		eventQueues.forEach(service::execute);

		registerEventTriggerService(eventQueues, execution);
	}

	private void registerEventTriggerService(List<EventQueue> queues, AssemblyLineExecution execution) {
		execution.registerEventTriggerService(new SimpleEventTriggerService(queues));
	}

	private static EventQueue buildQueue(Queue queue) {
		return new EventQueue(queue.getName(), queue.getEventListeners(), queue.getFilters(), new ParallelConfiguration(
				queue.isParallelExecution(), queue.getInitialThreadNumber(), queue.getMaxThreadNumber()));
	}

	public static class ParallelConfiguration {

		private boolean parallelExecution;
		private Integer initialThreadNumber;
		private Integer maxThreadNumber;

		public ParallelConfiguration(boolean parallelExecution, Integer initialThreadNumber, Integer maxThreadNumber) {
			this.parallelExecution = parallelExecution;
			this.initialThreadNumber = initialThreadNumber;
			this.maxThreadNumber = maxThreadNumber;
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

	}

}
