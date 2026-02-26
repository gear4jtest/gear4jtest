package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.model.ExecutionContext;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Boîte à outils technique du framework (Services).
 * Séparée du modèle de données (ExecutionContext) pour éviter le couplage.
 */
public final class ExecutionSupport {
    
    private final ExecutorDecorator executorDecorator;
    private final TaskFactory taskFactory;

    public ExecutionSupport(ExecutorDecorator executorDecorator, TaskFactory taskFactory) {
        this.executorDecorator = executorDecorator != null ? executorDecorator : ExecutorDecorator.noOp();
        this.taskFactory = taskFactory != null ? taskFactory : new TaskFactory();
    }

    /**
     * Fournit un ExecutorService prêt à l'emploi pour le framework.
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
}