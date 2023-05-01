package io.github.gear4jtest.core.event;

import java.util.List;

import io.github.gear4jtest.core.processor.BaseProcessingContext;

public class EventTriggerService {

	private List<EventQueue> eventQueues;

	public void triggerEvent(String eventName, BaseProcessingContext<?> ctx) {
		triggerEvent(eventName, null, ctx);
	}

	public void triggerEvent(String eventName, Object contextualData, BaseProcessingContext<?> ctx) {
		Event event = buildEvent(eventName, contextualData, ctx);
		for (EventQueue eventQueue : eventQueues) {
			eventQueue.pushEvent(event);
		}
	}

	private static Event buildEvent(String eventName, Object contextualData, BaseProcessingContext<?> ctx) {
		Event event = new Event();
		event.setEventName(eventName);
		event.setContexttualData(contextualData);
		event.setContext(ctx);
		return event;
	}

}
