package io.github.gear4jtest.core.model.refactor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public class DefaultOperationExecutionContext implements OperationExecutionContext {

    private final String operationId;
    private final OperationKind kind;
    private final ExecutionContext globalContext;
    private final OperationExecutionRecord record;

    private final Map<Class<?>, Object> capabilities = new HashMap<>();

    public DefaultOperationExecutionContext(
            String operationId,
            OperationKind kind,
            ExecutionContext globalContext,
            OperationExecutionRecord record
    ) {
        this.operationId = operationId;
        this.kind = kind;
        this.globalContext = globalContext;
        this.record = record;
    }

    @Override
    public String getOperationId() {
        return operationId;
    }

    @Override
    public OperationKind getKind() {
        return kind;
    }

    @Override
    public ExecutionContext getGlobalContext() {
        return globalContext;
    }

    @Override
    public OperationExecutionRecord getRecord() {
        return record;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCapability(Class<T> type) {
        return Optional.ofNullable((T) capabilities.get(type));
    }

    /**
     * API interne pour enrichir le contexte avec des capabilities typées.
     * À utiliser depuis les OperationDefinition concrètes.
     */
    public <T> void addCapability(Class<T> type, T instance) {
        capabilities.put(type, instance);
    }
}
