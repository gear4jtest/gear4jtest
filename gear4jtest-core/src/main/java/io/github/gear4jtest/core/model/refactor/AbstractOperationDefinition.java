package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
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
    protected Boolean unary;

    protected AbstractOperationDefinition(String id, OperationKind kind) {
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
        String parentId = context.getCurrentParentOperationId();
        var record = OperationExecutionRecord.start(
                context.getExecutionId().toString(),
                id,
                parentId
        );

        // PROPAGE le scope item courant
        record.setItemId(context.getCurrentItemId());

        var ctx = new DefaultOperationExecutionContext(id, kind, context, record);
        context.pushParentOperationId(record.getId());

        // Event : started
        if (context.getEventManager() != null) {
            context.getEventManager().publish(new OperationStartedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input));
        }

        if (context.getExecutionManager() != null) {
            context.getExecutionManager().append(record);
        }

        O result = null;
        Exception mainException = null;
        try {
            setUp(input, context, ctx);
            if (conditions != null && !conditions.isEmpty()) {
                boolean allMatch = true;
                for (Condition<I> condition : conditions) {
                    if (condition != null && !condition.test(input, context)) {
                        allMatch = false;
                        break;
                    }
                }

                if (!allMatch) {
                    // Conditions KO => fallback éventuel, ou unary, ou SKIPPED
                    result = handleSkippedByCondition(input, context, ctx, record);
                    return record;
                }
            }

            // Processors pré-exécution
            if (processors != null && !processors.isEmpty()) {
                for (Processor processor : processors) {
                    try {
                        processor.beforeExecution(input, ctx);
                    } catch (Exception e) {
                        // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                        record.addErrorHandlerException(e);
                    }
                }
            }

            result = doExecute(input, context, ctx);
            record.markSuccess(result);

            if (processors != null && !processors.isEmpty()) {
                for (Processor processor : processors) {
                    try {
                        processor.afterExecution(input, ctx);
                    } catch (Exception e) {
                        // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                        record.addErrorHandlerException(e);
                    }
                }
            }

            // Event : completed
            if (context.getEventManager() != null) {
                context.getEventManager().publish(new OperationCompletedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input, result));
            }

        }  catch (Exception e) {
            mainException = e;
            result = handleException(input, context, ctx, record, e);
        } finally {
            context.popParentOperationId();
            // Hook release, toujours appelé
            try {
                List<Throwable> errorsForRelease = buildErrorListForRelease(record, mainException);
                release(ctx, result, errorsForRelease);
            } catch (Exception releaseEx) {
                // On trace les erreurs de release comme "errorHandlerException"
                record.addErrorHandlerException(releaseEx);
            }
        }

        return record;
    }


    // -------------------------------------------------------------
    //              LOGIQUE DE SKIP (conditions KO)
    // -------------------------------------------------------------

    /**
     * Conditions KO : on applique la même logique que pour un "skip propre".
     * - fallbackTransformer si présent → SUCCESS
     * - sinon unary = true → SUCCESS avec input
     * - sinon SKIPPED
     */
    @SuppressWarnings("unchecked")
    protected O handleSkippedByCondition(I input,
                                         ExecutionContext context,
                                         OperationExecutionContext opContext,
                                         OperationExecutionRecord record) {
        O result = null;

        if (fallbackTransformer != null) {
            try {
                result = fallbackTransformer.transform(input, context, opContext);
                record.markSuccess(result);
            } catch (Exception e) {
                // Échec du fallback => on log l'erreur mais on marque SKIPPED
                record.addErrorHandlerException(e);
                record.markSkipped(e);
            }
            return result;
        }

        if (Boolean.TRUE.equals(unary)) {
            result = (O) input;
            record.markSuccess(result);
            return result;
        }

        // Simple skip
        record.markSkipped();
        return null;
    }

    /**
     * Gestion centralisée d'une exception levée pendant l'exécution.
     * On applique les règles onErrors dans l'ordre de déclaration.
     *
     * Règle de base :
     *  - Si aucune règle ne matche → FAILED
     *  - Si une règle matche :
     *      * IGNORE → fallback / unary / SKIPPED
     *      * STOP   → STOPPED
     *      * FATAL  → FAILED
     */
    @SuppressWarnings("unchecked")
    protected O handleException(I input,
                                ExecutionContext context,
                                OperationExecutionContext opContext,
                                OperationExecutionRecord record,
                                Exception exception) {

        // On publie l'évènement d'erreur quoi qu'il arrive
        if (context.getEventManager() != null) {
            context.getEventManager()
                    .publish(new OperationErrorEvent(
                            context.getPipelineId(),
                            context.getExecutionId().toString(),
                            id,
                            input,
                            exception
                    ));
        }

        // Aucune stratégie onError : comportement par défaut
        if (onErrors == null || onErrors.isEmpty()) {
            record.markFailed(exception);
            return null;
        }

        BaseError<I> matched = null;

        for (BaseError<I> error : onErrors) {
            if (error == null) {
                continue;
            }

            // Filtre sur le type de throwable
            Class<? extends Throwable> t = error.getThrowableType();
            if (t != null && !t.isAssignableFrom(exception.getClass())) {
                continue;
            }

            // Filtre sur la condition (si présente)
            Condition<I> cond = error.getCondition();
            if (cond != null && !cond.test(input, context)) {
                continue;
            }

            matched = error;
            break;
        }

        if (matched == null) {
            // Aucun handler ne matche : FAILED
            record.markFailed(exception);
            return null;
        }

        // Exécution de l'action associée, en "safe mode" :
        // toute erreur d'action est loggée dans le record mais ne remplace pas l'erreur principale
        try {
            if (matched.getAction() != null) {
                matched.getAction().run();
            }
        } catch (Exception handlerEx) {
            record.addErrorHandlerException(handlerEx);
        }

        var signal = matched.getSignalType();
        if (signal == null) {
            signal = SignalType.FATAL;
        }

        return switch (signal) {
            case IGNORE -> handleSkippedByCondition(input, context, opContext, record);
            case STOP -> {
                record.markStopped(exception);
                yield null;
                // On arrête logiquement le pipeline (via le status STOPPED)
            }
            default -> {
                record.markFailed(exception);
                yield null;
                // Comportement classique : FAILED
            }
        };
    }

    // -------------------------------------------------------------
    //              OUTILS POUR release(...)
    // -------------------------------------------------------------

    /**
     * Construit la liste des erreurs transmise à release :
     *  - les throwables enregistrés par addErrorHandlerException
     *  - sinon, l'exception principale le cas échéant
     */
    protected List<Throwable> buildErrorListForRelease(OperationExecutionRecord record,
                                                       Exception mainException) {
        List<Throwable> throwables = record.getThrowables();
        if (throwables == null || throwables.isEmpty()) {
            if (mainException == null) {
                return List.of();
            }
            List<Throwable> result = new ArrayList<>();
            result.add(mainException);
            return result;
        }
        // On ne rajoute l'exception principale que si elle n'est pas déjà dedans
        if (mainException != null && !throwables.contains(mainException)) {
            List<Throwable> result = new ArrayList<>(throwables);
            result.add(mainException);
            return result;
        }
        return throwables;
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
