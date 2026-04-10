package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import java.util.concurrent.ExecutorService;

/**
 * Boîte à outils technique du framework (Services).
 * Séparée du modèle de données (ExecutionContext) pour éviter le couplage.
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

    public PayloadCloner getPayloadCloner() {
        return this.payloadCloner;
    }
}
