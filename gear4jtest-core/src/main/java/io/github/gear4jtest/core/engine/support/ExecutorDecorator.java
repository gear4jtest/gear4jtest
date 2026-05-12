package io.github.gear4jtest.core.engine.support;

import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Internal hook used to decorate an {@link ExecutorService} before the engine
 * submits framework tasks to it.
 */
@FunctionalInterface
public interface ExecutorDecorator {

    /**
     * Returns the no-op decorator.
     */
    static ExecutorDecorator noOp() {
        return (raw, ctx) -> raw;
    }

    ExecutorService decorate(ExecutorService rawExecutor, ExecutionContext ctx);
}
