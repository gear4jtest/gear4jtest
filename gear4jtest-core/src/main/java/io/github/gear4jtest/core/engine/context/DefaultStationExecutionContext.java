package io.github.gear4jtest.core.engine.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ResolvedParameters;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

public class DefaultStationExecutionContext implements EngineStationExecutionContext {
    private final String operationId;
    private final StationKind kind;
    private final ExecutionContext globalContext;
    private final StationLogTrace trace;
    private final ExecutionSupport support;
    private final Map<Class<?>, Object> capabilities = new HashMap<>();

    public DefaultStationExecutionContext(String operationId,
                                          StationKind kind,
                                          ExecutionContext globalContext,
                                          StationLogTrace trace,
                                          ExecutionSupport support) {
        this.operationId = operationId;
        this.kind = kind;
        this.globalContext = globalContext;
        this.trace = trace;
        this.support = support;
    }

    public DefaultStationExecutionContext(String operationId,
                                          ExecutionContext globalContext,
                                          ExecutionSupport support) {
        this.operationId = operationId;
        this.kind = StationKind.ROOT;
        this.globalContext = globalContext;
        this.support = support;
        this.trace = null;
    }

    @Override
    public String getOperationId() {
        return operationId;
    }

    @Override
    public StationKind getKind() {
        return kind;
    }

    @Override
    public ExecutionContext getGlobalContext() {
        return globalContext;
    }

    @Override
    public StationLogTrace getRecord() {
        return trace;
    }

    @Override
    public ExecutionSupport getSupport() {
        return support;
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> type) {
        return Optional.ofNullable(type.cast(capabilities.get(type)));
    }

    @Override
    public <T> void addCapability(Class<T> type, T instance) {
        capabilities.put(type, instance);
    }

    @Override
    public ResolvedParameters getResolvedParameters() {
        return getCapability(ResolvedParameters.class).orElseGet(() -> {
            ResolvedParameters cache = new ResolvedParameters();
            addCapability(ResolvedParameters.class, cache);
            return cache;
        });
    }
}
