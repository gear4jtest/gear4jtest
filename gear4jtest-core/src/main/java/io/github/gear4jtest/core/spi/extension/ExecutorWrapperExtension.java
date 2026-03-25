package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import java.util.concurrent.ExecutorService;

public interface ExecutorWrapperExtension extends RuntimeExtension {
    ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx);
}
