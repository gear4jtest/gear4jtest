package io.github.gear4jtest.core.event;

import java.time.Instant;
import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public class Event {
    private final UUID id;
    private final String assemblyLineId;
    private final UUID executionId;
    private final Instant occurredAt;
    private final String type;

    public Event(String assemblyLineId, UUID executionId) {
        this(assemblyLineId, executionId, null);
    }

    public Event(String assemblyLineId, UUID executionId, String type) {
        this.id = UUID.randomUUID();
        this.assemblyLineId = assemblyLineId;
        this.executionId = executionId;
        this.occurredAt = Instant.now();
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public String getAssemblyLineId() {
        return assemblyLineId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getName() {
        return type != null ? type : getClass().getSimpleName();
    }
}
