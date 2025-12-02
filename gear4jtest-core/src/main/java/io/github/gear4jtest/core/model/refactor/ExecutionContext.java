package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.persistence.PipelineExecution;
import io.github.gear4jtest.core.sidecompute.SideComputeContext;

public class ExecutionContext {

    private final ThreadLocal<String> currentItemId = new ThreadLocal<>();
    private final ThreadLocal<Deque<String>> parentStack = ThreadLocal.withInitial(ArrayDeque::new);

    private final UUID executionId;
    private final String pipelineId;
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final EventManager eventManager;
    private final PipelineExecutionManager executionManager;
    private final ResourceFactory resourceFactory;
    private final SideComputeContext sideComputeContext = new SideComputeContext();
    private final PipelineExecution pipelineExecution;

    public ExecutionContext(UUID executionId,
                            String pipelineId,
                            EventManager eventManager,
                            ResourceFactory resourceFactory,
                            PipelineExecutionManager executionManager,
                            PipelineExecution pipelineExecution) {
        this.pipelineId = pipelineId;
        this.executionId = executionId;
        this.eventManager = eventManager;
        this.resourceFactory = resourceFactory;
        this.executionManager = executionManager;
        this.pipelineExecution = pipelineExecution;
    }

    public String getPipelineId() {
        return pipelineId;
    }
    public <T> void put(String key, T value) { context.put(key, value); }
    public <T> T get(String key, Class<T> type) { return type.cast(context.get(key)); }
    public EventManager getEventManager() { return eventManager; }
    public UUID getExecutionId() { return executionId; }
    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }
    public Map<String, Object> getContext() {
        return context;
    }

    public PipelineExecutionManager getExecutionManager() { return executionManager; }
    public SideComputeContext getSideComputeContext() {
        return sideComputeContext;
    }

    public String getCurrentItemId() {
        return currentItemId.get();
    }

    public PipelineExecution getPipelineExecution() {
        return pipelineExecution;
    }

    public void setCurrentItemId(String itemId) {
        if (itemId == null) {
            currentItemId.remove();
        } else {
            currentItemId.set(itemId);
        }
    }

    void withItemId(String itemId, Runnable action) {
        String previous = getCurrentItemId();
        setCurrentItemId(itemId);
        try {
            action.run();
        } finally {
            setCurrentItemId(previous);
        }
    }

    <T> T withItemId(String itemId, Supplier<T> action) {
        String previous = getCurrentItemId();
        setCurrentItemId(itemId);
        try {
            return action.get();
        } finally {
            setCurrentItemId(previous);
        }
    }

    public String getCurrentParentOperationId() {
        Deque<String> stack = parentStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public void pushParentOperationId(String operationId) {
        parentStack.get().push(operationId);
    }

    public void popParentOperationId() {
        Deque<String> stack = parentStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    void withParentOperation(String operationId, Runnable action) {
        pushParentOperationId(operationId);
        try {
            action.run();
        } finally {
            popParentOperationId();
        }
    }
}
