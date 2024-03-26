package io.github.gear4jtest.core.event.builders;

import java.util.UUID;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBuilder;
import io.github.gear4jtest.core.event.builders.OperationProcessorEventBuilder.OperationProcessorData;
import io.github.gear4jtest.core.event.builders.OperationProcessorEventBuilder.OperationProcessorEvent;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationProcessorEventBuilder implements EventBuilder<OperationProcessorData, OperationProcessorEvent> {

	private static final String EVENT_NAME = "OPERATION_PROCESSOR";

	@Override
	public OperationProcessorEvent buildEvent(UUID uuid, OperationProcessorData data) {
		return new OperationProcessorEvent(data, uuid);
	}

	@Override
	public String eventName() {
		return EVENT_NAME;
	}

	public class OperationProcessorEvent extends Event {

		private ProcessorResult processorResult;

		public OperationProcessorEvent(OperationProcessorData data, UUID uuid) {
			super(OperationProcessorEventBuilder.this.eventName(), uuid);
			this.processorResult = data.processorResult;
		}

		public ProcessorResult getProcessorResult() {
			return processorResult;
		}

	}

	public static class OperationProcessorData {
		private ProcessorResult processorResult;

		public OperationProcessorData(ProcessorResult parameter) {
			this.processorResult = parameter;
		}

	}

}
