package io.github.gear4jtest.core.spi.extension;

import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.context.ExecutionContext;

public interface ExecutorWrapperExtension extends RuntimeExtension {
    ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx);
}
