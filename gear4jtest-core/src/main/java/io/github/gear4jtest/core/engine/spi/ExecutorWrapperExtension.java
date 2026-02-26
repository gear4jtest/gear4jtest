package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.model.ExecutionContext;
import java.util.concurrent.ExecutorService;

public interface ExecutorWrapperExtension extends RuntimeExtension {
    ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx);
}
