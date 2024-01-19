package io.github.gear4jtest.core.context;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.event.EventTriggerService;

public class LineElementExecution {

	private final ItemExecution itemExecution;
	
	private Object result;

	private Map<String, Object> context;

	private Throwable throwable;

//	private SourceExhauster sourceExhauster;

	public LineElementExecution(ItemExecution itemExecution) {
		this.itemExecution = itemExecution;
		this.context = new HashMap<>();
	}

	public ItemExecution getItemExecution() {
		return itemExecution;
	}

	public Object getResult() {
		return result;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public EventTriggerService getEventTriggerService() {
		return itemExecution.getEventTriggerService();
	}
	
	public void registerThrowable(Throwable throwable) {
		this.throwable = throwable;
		itemExecution.getAssemblyLineExecution().registerThrowable(throwable);
	}

	public void setResult(Object result) {
		this.result = result;
	}

}
