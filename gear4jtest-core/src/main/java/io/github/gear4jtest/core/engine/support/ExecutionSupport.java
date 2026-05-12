package io.github.gear4jtest.core.engine.support;

import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;

/**
 * Passive framework helpers used during execution.
 *
 * <p>
 * Unlike run-scoped {@code ExecutionServices}, this object groups stateless or
 * near-stateless helpers such as task creation, executor decoration and payload
 * cloning.
 * </p>
 */
public final class ExecutionSupport {
    private final ExecutorDecorator executorDecorator;
    private final TaskFactory taskFactory;
    private final PayloadCloner payloadCloner;

    public ExecutionSupport(ExecutorDecorator executorDecorator, TaskFactory taskFactory, PayloadCloner payloadCloner) {
        this.executorDecorator = executorDecorator != null ? executorDecorator : ExecutorDecorator.noOp();
        this.taskFactory = taskFactory != null ? taskFactory : new TaskFactory();
        this.payloadCloner = payloadCloner != null ? payloadCloner : PayloadCloners.immutableAware();
    }

    /**
     * Returns the executor that should be used by the framework for the current
     * run.
     */
    public ExecutorService executorFor(ExecutorService rawExecutor, ExecutionContext ctx) {
        if (rawExecutor == null) {
            return null;
        }
        return executorDecorator.decorate(rawExecutor, ctx);
    }

    public TaskFactory getTaskFactory() {
        return this.taskFactory;
    }

    public PayloadCloner getPayloadCloner() {
        return this.payloadCloner;
    }
}
