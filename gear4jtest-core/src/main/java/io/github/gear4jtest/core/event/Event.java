package io.github.gear4jtest.core.event;

import java.util.UUID;

public class Event {

	private final UUID id;
	private final String pipelineId;
	private final String executionId;
	private final String type;

	public Event(String pipelineId, String executionId, String type) {
		this.id = UUID.randomUUID();
		this.pipelineId = pipelineId;
		this.executionId = executionId;
		this.type = type;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return type;
	}

}
