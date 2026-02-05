package io.github.gear4jtest.core.engine.core;

import java.util.UUID;

public class ContextInfo {
    
    private final String executionId; // ID unique du run (Trace ID)
    private final String userId;
    private final String tenantId;

    public ContextInfo(String executionId, String userId, String tenantId) {
        this.executionId = executionId != null ? executionId : UUID.randomUUID().toString();
        this.userId = userId;
        this.tenantId = tenantId;
    }

    public static ContextInfo anonymous() {
        return new ContextInfo(UUID.randomUUID().toString(), "anonymous", "default");
    }

    // Getters...
    public String getExecutionId() { return executionId; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
}