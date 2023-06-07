package io.github.gear4jtest.core.event.builders;

import java.util.UUID;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBuilder;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterContextualData;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterInjectionEvent;

public class ParameterInjectionEventBuilder implements EventBuilder<ParameterContextualData, ParameterInjectionEvent> {

	private static final String EVENT_NAME = "PARAMETER_INJECTION";

	@Override
	public ParameterInjectionEvent buildEvent(UUID uuid, ParameterContextualData data) {
		return new ParameterInjectionEvent(data, uuid);
	}

	@Override
	public String eventName() {
		return EVENT_NAME;
	}

	public class ParameterInjectionEvent extends Event {

		private String parameter;
		private Object value;

		public ParameterInjectionEvent(ParameterContextualData data, UUID uuid) {
			super(ParameterInjectionEventBuilder.this.eventName(), uuid);
			this.parameter = data.parameter;
			this.value = data.value;
		}

		public String getParameter() {
			return parameter;
		}

		public Object getValue() {
			return value;
		}

	}

	public static class ParameterContextualData {
		private String parameter;
		private Object value;

		public ParameterContextualData(String parameter, Object value) {
			this.parameter = parameter;
			this.value = value;
		}

	}

}
