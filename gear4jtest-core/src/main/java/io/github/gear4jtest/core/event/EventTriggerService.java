package io.github.gear4jtest.core.event;

import java.util.List;

import io.github.gear4jtest.core.context.Contexts;

public class EventTriggerService {

	private final List<EventQueue> eventQueues;

	public EventTriggerService(List<EventQueue> eventQueues) {
		this.eventQueues = eventQueues;
	}

	public void triggerEvent(String eventName, Contexts<?> ctx) {
		triggerEvent(eventName, null, ctx);
	}

	public void triggerEvent(String eventName, Object contextualData, Contexts<?> ctx) {
		if (eventQueues == null) {
			return;
		}
		
		Contexts<?> ctxs = ctx.clone();
		
		Event event = buildEvent(eventName, contextualData, ctxs);
		for (EventQueue eventQueue : eventQueues) {
			eventQueue.pushEvent(event);
		}
	}

	private static Event buildEvent(String eventName, Object contextualData, Contexts<?> ctx) {
		Event event = new Event();
		event.setEventName(eventName);
		event.setContextualData(contextualData);
		event.setContext(ctx);
		return event;
	}

}
