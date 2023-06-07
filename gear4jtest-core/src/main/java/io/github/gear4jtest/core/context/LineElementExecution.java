package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;

public class LineElementExecution {

	private ItemExecution itemExecution;

	private Map<String, Object> context;

	public LineElementExecution(ItemExecution itemExecution) {
		this.itemExecution = itemExecution;
		this.context = new HashMap<>();
	}

	public ItemExecution getItemExecution() {
		return itemExecution;
	}

	public Map<String, Object> getContext() {
		return context;
	}
	
	public EventTriggerService getEventTriggerService() {
		return itemExecution.getEventTriggerService();
	}

}