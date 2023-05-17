package io.github.gear4jtest.core.event;

public class Event {

    private String eventName;
    private Object contextualData;

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
    
}
