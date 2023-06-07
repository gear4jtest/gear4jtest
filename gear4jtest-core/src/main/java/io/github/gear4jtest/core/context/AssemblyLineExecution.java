package io.github.gear4jtest.core.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventTriggerService;
import io.github.gear4jtest.core.internal.ServiceRegistry;

public class AssemblyLineExecution {

	private UUID id;

	private LocalDateTime startTime = null;

	private LocalDateTime createTime = LocalDateTime.now();

	private LocalDateTime endTime = null;

	private LocalDateTime lastUpdateTime = null;

	private List<ItemExecution> itemExecutions;

	private Map<String, Object> context;

	public AssemblyLineExecution(Map<String, Object> context, UUID id) {
		this.id = id;
		this.context = context;
		this.itemExecutions = new ArrayList<>();
	}

	public ItemExecution createItemExecution() {
		return createItemExecution(new HashMap<>());
	}

	public ItemExecution createItemExecution(Map<String, Object> context) {
		ItemExecution itemExecution = new ItemExecution(this, context);
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

	public EventTriggerService getEventTriggerService() {
		return ServiceRegistry.getEventPublisher(id);
	}

}
