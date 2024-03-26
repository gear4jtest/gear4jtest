package io.github.gear4jtest.core.event.builders;

import java.util.UUID;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBuilder;
import io.github.gear4jtest.core.event.builders.AssemblyLineCreationEventBuilder.AssemblyLineData;
import io.github.gear4jtest.core.event.builders.AssemblyLineCreationEventBuilder.AssemblyLineEvent;
import io.github.gear4jtest.core.internal.AssemblyLine;

public class AssemblyLineCreationEventBuilder implements EventBuilder<AssemblyLineData, AssemblyLineEvent> {

	private static final String EVENT_NAME = "ASSEMBLY_LINE_CREATION";

	@Override
	public AssemblyLineEvent buildEvent(UUID uuid, AssemblyLineData data) {
		return new AssemblyLineEvent(data, uuid);
	}

	@Override
	public String eventName() {
		return EVENT_NAME;
	}

	public class AssemblyLineEvent extends Event {

		private AssemblyLine processorResult;

		public AssemblyLineEvent(AssemblyLineData data, UUID uuid) {
			super(AssemblyLineCreationEventBuilder.this.eventName(), uuid);
			this.processorResult = data.processorResult;
		}

		public AssemblyLine getProcessorResult() {
			return processorResult;
		}

	}

	public static class AssemblyLineData {
		private AssemblyLine processorResult;

		public AssemblyLineData(AssemblyLine parameter) {
			this.processorResult = parameter;
		}

	}

}
