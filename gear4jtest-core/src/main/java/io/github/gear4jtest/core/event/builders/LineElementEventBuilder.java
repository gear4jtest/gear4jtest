package io.github.gear4jtest.core.event.builders;

import java.util.UUID;

import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBuilder;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder.LineElementExecutionData;
import io.github.gear4jtest.core.event.builders.LineElementEventBuilder.LineElementExecutionEvent;

public class LineElementEventBuilder implements EventBuilder<LineElementExecutionData, LineElementExecutionEvent> {

	private static final String EVENT_NAME = "OPERATION_PROCESSOR";

	@Override
	public LineElementExecutionEvent buildEvent(UUID uuid, LineElementExecutionData data) {
		return new LineElementExecutionEvent(data, uuid);
	}

	@Override
	public String eventName() {
		return EVENT_NAME;
	}

	public class LineElementExecutionEvent extends Event {

		private LineElementExecution processorResult;

		public LineElementExecutionEvent(LineElementExecutionData data, UUID uuid) {
			super(LineElementEventBuilder.this.eventName(), uuid);
			this.processorResult = data.processorResult;
		}

		public LineElementExecution getProcessorResult() {
			return processorResult;
		}

	}

	public static class LineElementExecutionData {
		private LineElementExecution processorResult;

		public LineElementExecutionData(LineElementExecution parameter) {
			this.processorResult = parameter;
		}

	}

}
