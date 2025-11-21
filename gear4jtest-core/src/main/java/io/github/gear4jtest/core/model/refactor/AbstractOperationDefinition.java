package io.github.gear4jtest.core.model.refactor;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.event.OperationErrorEvent;
import io.github.gear4jtest.core.event.OperationStartedEvent;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

/**
 * Template commun à toutes les OperationDefinition.
 * - Gère la création du record
 * - Gère les events
 * - Gère les processors
 * - Gère le wiring avec le PipelineExecutionManager
 */
public abstract class AbstractOperationDefinition<I, O> implements OperationDefinition<I, O> {

    protected String id;
    protected OperationKind kind;
    protected List<Processor> processors;
    protected List<BaseError<I>> onErrors;
    protected List<Condition<I>> conditions;
    protected Transformer<I, O> fallbackTransformer;

    protected AbstractOperationDefinition(String id,
                                          OperationKind kind) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.kind = Objects.requireNonNull(kind, "kind is required");
    }

    public String getId() {
        return id;
    }

    public OperationKind getKind() {
        return kind;
    }

    protected List<Processor> getProcessors() {
        return processors;
    }

    @Override
    public final OperationExecutionRecord run(I input, ExecutionContext context) {
        var record = OperationExecutionRecord.start(
                context.getExecutionId().toString(),
                id,
                null // parent sera éventuellement posé par un container
        );

        var ctx = new DefaultOperationExecutionContext(id, kind, context, record);

        // Event : started
        if (context.getEventManager() != null) {
            context.getEventManager().publish(new OperationStartedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input));
        }

        O result = null;
        try {
            setUp(input, context, ctx);
            for (Condition<I> condition : conditions) {
                if (condition != null && !condition.test(input, context)) {
                    if (fallbackTransformer != null) {
                        result = fallbackTransformer.transform(input, context, ctx);
                        record.markSuccess(result);
                        return record;
                    }
                    throw new RuntimeException("Operation skipped without transformer");
                }
            }

            // Processors pré-exécution
            for (Processor processor : processors) {
                try {
                    processor.beforeExecution(input, ctx);
                } catch (Exception e) {
                    // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                    record.addErrorHandlerException(e);
                }
            }

            result = doExecute(input, context, ctx);
            record.markSuccess(result);

            for (Processor processor : processors) {
                try {
                    processor.afterExecution(input, ctx);
                } catch (Exception e) {
                    // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                    record.addErrorHandlerException(e);
                }
            }

            // Event : completed
            if (context.getEventManager() != null) {
                context.getEventManager().publish(new OperationCompletedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input, result));
            }

        } catch (Exception e) {
            record.markFailed(e);
            if (context.getEventManager() != null) {
                context.getEventManager().publish(new OperationErrorEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input, e));
            }
        } finally {
            // 5. release (unlock, cleanup, etc.)
            release(ctx, result, record.getThrowables());
        }

        // Append dans l'execution manager, s'il y en a un
        if (context.getExecutionManager() != null) {
            context.getExecutionManager().append(record);
        }

        return record;
    }

    /**
     * Hook d'initialisation par exécution.
     *
     * Appelé AVANT les preProcessors et AVANT la logique métier.
     * C'est ici que les sous-classes peuvent acquérir des ressources (locks, etc.).
     */
    protected void setUp(I input, ExecutionContext context, OperationExecutionContext operationExecution) {
        // no-op par défaut
    }

    /**
     * Hook de libération par exécution.
     *
     * Appelé APRÈS les postProcessors, dans le bloc finally.
     * Reçoit éventuellement le résultat et/ou l'erreur si une exception a été levée.
     */
    protected void release(OperationExecutionContext context, O result, List<Throwable> errors) {
        // no-op par défaut
    }

    /**
     * Implémentation réelle de l'opération métier.
     * Les classes concrètes manipulent librement le contexte
     * (ex : ajout de capabilities) avant de renvoyer le résultat.
     */
    protected abstract O doExecute(I input,
                                   ExecutionContext globalContext,
                                   OperationExecutionContext opContext) throws Exception;
}
