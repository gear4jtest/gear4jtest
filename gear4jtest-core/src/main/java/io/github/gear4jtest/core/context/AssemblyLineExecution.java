package io.github.gear4jtest.core.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.event.NoOpEventTriggerService;
import io.github.gear4jtest.core.event.SimpleEventTriggerService;

public class AssemblyLineExecution {

	private UUID id;
	
	//TODO(all): make this execution be linked with an instance of the assembly line (useful for batch restarting)...
//	private AssemblyLine line;

	private LocalDateTime startTime = null;

	private LocalDateTime createTime = LocalDateTime.now();

	private LocalDateTime endTime = null;

	private LocalDateTime lastUpdateTime = null;

	private ItemExecution itemExecution;

	private Map<String, Object> context;
	
	private EventTriggerService eventTriggerService;
	
	private List<Throwable> throwables = new ArrayList<>();

	public AssemblyLineExecution(Map<String, Object> context) {
		this.id = UUID.randomUUID();
		this.context = context;
		this.eventTriggerService = new NoOpEventTriggerService();
	}

	public ItemExecution createItemExecution(Object input) {
		return createItemExecution(input, new HashMap<>());
	}

	public ItemExecution createItemExecution(Object input, Map<String, Object> context) {
		ItemExecution itemExecution = new ItemExecution(this, input, context);
		this.itemExecution = itemExecution;
		return itemExecution;
	}

	public UUID getId() {
		return id;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
		this.endTime = endTime;
	}

	public LocalDateTime getLastUpdateTime() {
		return lastUpdateTime;
	}

	public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
		this.lastUpdateTime = lastUpdateTime;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public ItemExecution getItemExecution() {
		return itemExecution;
	}

	public void registerEventTriggerService(SimpleEventTriggerService service) {
		this.eventTriggerService = service;
	}

	public EventTriggerService getEventTriggerService() {
//		return ServiceRegistry.getEventPublisher(id);
		return eventTriggerService;
	}

	public List<Throwable> getThrowables() {
		return throwables;
	}

	public void registerThrowable(Throwable throwable) {
		this.throwables.add(throwable);
	}

}
