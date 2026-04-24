package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import java.util.concurrent.ExecutorService;

/**
 * Contrat interne permettant de préparer un ExecutorService pour le framework 
 * (ex: propagation du MDC, du Tracing, etc.)
 */
@FunctionalInterface
public interface ExecutorDecorator {
    
    ExecutorService decorate(ExecutorService rawExecutor, ExecutionContext ctx);

    /**
     * Implémentation par défaut qui ne fait rien (No-Op).
     */
    static ExecutorDecorator noOp() {
        return (raw, ctx) -> raw;
    }
}
