package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;

public class Gear4jContext {

	private Map<String, Object> chainContext;
	
	private EventTriggerService eventTriggerService;
	
	public Gear4jContext clone() {
		return new Gear4jContext(new HashMap<>(chainContext), eventTriggerService);
	}

	public Gear4jContext(Map<String, Object> chainContext, EventTriggerService eventTriggerService) {
		this.chainContext = chainContext;
		this.eventTriggerService = eventTriggerService;
	}

	public Map<String, Object> getChainContext() {
		return chainContext;
	}

	public EventTriggerService getEventTriggerService() {
		return eventTriggerService;
	}
	
}
