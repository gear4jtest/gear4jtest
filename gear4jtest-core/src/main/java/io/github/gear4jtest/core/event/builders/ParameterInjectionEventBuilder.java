package io.github.gear4jtest.core.event.builders;

import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventBuilder;
import io.github.gear4jtest.core.event.builders.ParameterInjectionEventBuilder.ParameterInjectionEvent;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.LineElement;

public class ParameterInjectionEventBuilder implements EventBuilder<ParameterInjectionEvent> {

	private static final String EVENT_NAME = "PARAMETER_INJECTION";
	
	@Override
	public ParameterInjectionEvent buildEvent(Item item, LineElement element, Object... additionalObjects) {
		return new ParameterInjectionEvent((String) additionalObjects[0], additionalObjects[1]);
	}

	@Override
	public String eventName() {
		return EVENT_NAME;
	}

	public static class ParameterInjectionEvent extends Event {

		private String parameter;
		private Object value;

		public ParameterInjectionEvent(String parameter, Object value) {
			this.parameter = parameter;
			this.value = value;
		}

		public String getParameter() {
			return parameter;
		}

		public Object getValue() {
			return value;
		}

	}

}
