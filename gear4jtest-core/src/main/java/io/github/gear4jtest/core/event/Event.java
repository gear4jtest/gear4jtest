package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.context.Contexts;

public class Event {

    private String eventName;
    private Object contextualData;
    private Contexts<?> context;

	public String getEventName() {
		return eventName;
	}
	
	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
	
	public Object getContextualData() {
		return contextualData;
	}

	public void setContextualData(Object contextualData) {
		this.contextualData = contextualData;
	}

	public Contexts<?> getContext() {
		return context;
	}

	public void setContext(Contexts<?> ctx) {
		this.context = ctx;
	}
    
}
