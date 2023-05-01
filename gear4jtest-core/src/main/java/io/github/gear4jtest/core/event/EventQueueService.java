package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.model.Queue;

public class EventQueueService {
	
	private final List<Queue> queues;

	public EventQueueService(List<Queue> queues) {
		this.queues = queues;
	}

	public List<EventQueue> initializeQueues() {
		if (queues == null) {
			return null;
		}
		List<EventQueue> eventQueues = queues.stream().map(EventQueueService::buildQueue).collect(Collectors.toList());
		ExecutorService service = Executors.newFixedThreadPool(queues.size());
		eventQueues.forEach(service::execute);
		return eventQueues;
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
