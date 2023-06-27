package io.github.gear4jtest.core.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventTriggerService;

public class AssemblyLineExecution {

	private UUID id;
	
	//TODO(all): make this execution be linked with an instance of the assembly line (useful for batch restarting)...
//	private AssemblyLine line;

	private LocalDateTime startTime = null;

	private LocalDateTime createTime = LocalDateTime.now();

	private LocalDateTime endTime = null;

	private LocalDateTime lastUpdateTime = null;

	private List<ItemExecution> itemExecutions;

	private Map<String, Object> context;
	
	private EventTriggerService eventTriggerService;

	public AssemblyLineExecution(Map<String, Object> context) {
		this.id = UUID.randomUUID();
		this.itemExecutions = new ArrayList<>();
		this.context = context;
	}

	public ItemExecution createItemExecution(Object input) {
		return createItemExecution(input, new HashMap<>());
	}

	public ItemExecution createItemExecution(Object input, Map<String, Object> context) {
		ItemExecution itemExecution = new ItemExecution(this, input, context);
		itemExecutions.add(itemExecution);
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
	
	public void registerEventTriggerService(EventTriggerService service) {
		this.eventTriggerService = service;
	}

	public EventTriggerService getEventTriggerService() {
//		return ServiceRegistry.getEventPublisher(id);
		return eventTriggerService;
	}

}
