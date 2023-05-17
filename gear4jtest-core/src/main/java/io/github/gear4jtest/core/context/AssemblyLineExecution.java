package io.github.gear4jtest.core.context;

import java.time.LocalDateTime;
import java.util.Map;

public class AssemblyLineExecution {

	private LocalDateTime startTime = null;

	private LocalDateTime createTime = LocalDateTime.now();

	private LocalDateTime endTime = null;

	private LocalDateTime lastUpdateTime = null;

	private Map<String, Object> context;
	
	public AssemblyLineExecution(Map<String, Object> context) {
		this.context = context;
	}

	public ItemExecution createItemExecution() {
		return new ItemExecution(this);
	}
	
	public ItemExecution createItemExecution(Map<String, Object> context) {
		return new ItemExecution(this, context);
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

}
