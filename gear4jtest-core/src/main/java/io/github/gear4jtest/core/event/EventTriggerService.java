package io.github.gear4jtest.core.event;

import java.util.List;

public class EventTriggerService {

	private final List<EventQueue> eventQueues;
//	private final ResourceFactory resourceFactory;

	public EventTriggerService(List<EventQueue> eventQueues) {
		this.eventQueues = eventQueues;
//		this.resourceFactory = resourceFactory;
	}

//	public void publishEvent(Class<? extends EventBuilder<?>> eventBuilderClass, Item item, LineElement element, Object... additionalObjects) {
//		if (eventQueues == null) {
//			return;
//		}
//		
//		Event e = resourceFactory.getResource(eventBuilderClass).buildEvent(item, element, additionalObjects);
//		
//		for (EventQueue eventQueue : eventQueues) {
//			eventQueue.pushEvent(e);
//		}
//	}
	
	public void publishEvent(Event event) {
		if (eventQueues == null) {
			return;
		}
		
		for (EventQueue eventQueue : eventQueues) {
			eventQueue.pushEvent(event);
		}
	}

}
