package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.processor.BaseProcessingContext;

public class Event {

    private String eventName;
    private Object contexttualData;
    private BaseProcessingContext<?> context;

	public String getEventName() {
		return eventName;
	}
	
	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
	
	public Object getContexttualData() {
		return contexttualData;
	}

	public void setContexttualData(Object contexttualData) {
		this.contexttualData = contexttualData;
	}

	public BaseProcessingContext<?> getContext() {
		return context;
	}

	public void setContext(BaseProcessingContext<?> ctx) {
		this.context = ctx;
	}
    
}
