package io.github.gear4jtest.core.engine.strategies;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.event.OperationErrorEvent;
import io.github.gear4jtest.core.event.OperationStartedEvent;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.BaseError;
import io.github.gear4jtest.core.model.Condition;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.Processor;
import io.github.gear4jtest.core.model.SignalType;
import io.github.gear4jtest.core.model.SkipDecision;
import io.github.gear4jtest.core.model.SkipPhase;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.StationSkipper;
import io.github.gear4jtest.core.persistence.StationLog;

public abstract class AbstractStationStrategy<S extends AbstractStation> implements StationExecutionStrategy<S> {

    @Override
    public StationLog run(S station, Object input, StationExecutionContext context, StationRunner runner) {
//        String parentId = context.getGlobalContext().getCurrentParentOperationId();
//        var record = StationLog.start(
//                context.getGlobalContext().getExecutionId().toString(),
//                station.getId(),
//                parentId
//        );

        // PROPAGE le scope item courant
//        record.setItemId(context.getGlobalContext().getCurrentItemId());

//        var ctx = new DefaultStationExecutionContext(station.getId(), station.getKind(), context, record);
//        context.pushParentOperationId(record.getId());

        // Event : started
        if (context.getGlobalContext().getEventManager() != null) {
            context.getGlobalContext().getEventManager().publish(
                    new OperationStartedEvent(
                            context.getGlobalContext().getPipelineId(),
                            context.getGlobalContext().getExecutionId(),
                            station.getId(),
                            input));
        }

//        if (context.getAssemblyRunManager() != null) {
//            context.getAssemblyRunManager().append(record);
//        }

        Object result = null;
        Exception mainException = null;
        try {
            setUp(station, input, context);
//            if (station.getConditions() != null && !station.getConditions().isEmpty()) {
//                boolean allMatch = true;
//                for (Condition condition : (List<Condition>) station.getConditions()) {
//                    if (condition != null && !condition.test(input, context.getGlobalContext())) {
//                        allMatch = false;
//                        break;
//                    }
//                }
//
//                if (!allMatch) {
//                    // Conditions KO => fallback éventuel, ou unary, ou SKIPPED
//                    result = handleSkippedByCondition(station, input, context, context.getRecord());
//                    return context.getRecord();
//                }
//            }

            // 1) Skippers PRE
            SkipDecision preCause = runSkippers(station, input, context, SkipPhase.PRE_PROCESSORS);
            if (preCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), preCause.reason());
                return context.getRecord();
            }

            // Processors pré-exécution
            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : (List<Processor>) station.getProcessors()) {
                    try {
                        processor.beforeExecution(input, context);
                    } catch (Exception e) {
                        // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                        context.getRecord().addErrorHandlerException(e);
                    }
                }
            }

            // 3) Skippers POST
            SkipDecision postCause = runSkippers(station, input, context, SkipPhase.POST_PROCESSORS);
            if (preCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), postCause.reason());
                return context.getRecord();
            }

            result = doExecute(station, input, runner, context);

            // IMPORTANT : certaines strategies (conteneurs, signaux, ...) peuvent déjà avoir fixé
            // le statut du record (STOPPED/FAILED/CANCELLED). Dans ce cas, on ne doit PAS écraser
            // ce statut avec un markSuccess().
            if (context.getRecord().getStatus() == StationLog.Status.RUNNING) {
                context.getRecord().markSuccess(result);
            } else {
                // Par cohérence, on conserve l'output calculé si la strategy en renvoie un.
                // (ex : STOPPED peut vouloir garder le dernier output valide)
                context.getRecord().setOutput(result);
                // Si la strategy n'a pas fixé de endedAt, on le fait.
                if (context.getRecord().getEndedAt() == null) {
                    context.getRecord().setEndedAt(java.time.Instant.now());
                }
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : (List<Processor>) station.getProcessors()) {
                    try {
                        processor.afterExecution(input, context);
                    } catch (Exception e) {
                        // échec d'un processor = on marque en FAILED et on publie un event d'erreur
                        context.getRecord().addErrorHandlerException(e);
                    }
                }
            }

            // Event : completed
            if (context.getGlobalContext().getEventManager() != null) {
                context.getGlobalContext().getEventManager().publish(
                        new OperationCompletedEvent(
                                context.getGlobalContext().getPipelineId(),
                                context.getGlobalContext().getExecutionId(),
                                station.getId(),
                                input,
                                result));
            }

        }  catch (Exception e) {
            mainException = e;
            result = handleException(station, input, context.getGlobalContext(), context, context.getRecord(), e);
        } finally {
            context.getGlobalContext().popParentOperationId();
            // Hook release, toujours appelé
            try {
                List<Throwable> errorsForRelease = buildErrorListForRelease(context.getRecord(), mainException);
                release(station, result, context, errorsForRelease);
            } catch (Exception releaseEx) {
                // On trace les erreurs de release comme "errorHandlerException"
                context.getRecord().addErrorHandlerException(releaseEx);
            }
        }

        return context.getRecord();
    }

    protected SkipDecision runSkippers(S station, Object input, StationExecutionContext ctx, SkipPhase phase) {
        List<StationSkipper> skippers = station.getSkippers();
        if (skippers == null || skippers.isEmpty()) {
            return SkipDecision.dontSkip();
        }

        for (StationSkipper s : skippers) {
            if (s == null || s.phase() != phase) {
                continue;
            }
            SkipDecision skipDecision = s.shouldSkip(input, ctx);
            if (skipDecision.shouldSkip()) {
                return skipDecision;
            }
        }
        return SkipDecision.dontSkip();
    }

    protected Object handleSkip(S station,
                                Object input,
                                StationExecutionContext ctx,
                                StationLog record,
                                String reason) {
        if (station.getFallbackOperator() != null) {
            try {
                Object res = station.getFallbackOperator().transform(input, ctx);
                record.markSuccess(res);
                return res;
            } catch (Exception e) {
                record.addErrorHandlerException(e);
                record.markSkipped(e);
                return null;
            }
        }

        if (Boolean.TRUE.equals(station.getUnary())) {
            record.markSuccess(input);
            return input;
        }

        if (reason != null) {
            record.markSkipped(reason);
        } else {
            record.markSkipped();
        }
        return null;
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
    protected Object handleSkippedByCondition(S station,
                                              Object input,
                                              StationExecutionContext opContext,
                                              StationLog record) {
        Object result = null;

        if (station.getFallbackOperator() != null) {
            try {
                result = station.getFallbackOperator().transform(input, opContext);
                record.markSuccess(result);
            } catch (Exception e) {
                // Échec du fallback => on log l'erreur mais on marque SKIPPED
                record.addErrorHandlerException(e);
                record.markSkipped(e);
            }
            return result;
        }

        if (Boolean.TRUE.equals(station.getUnary())) {
            result = input;
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
    protected Object handleException(S station,
                                     Object input,
                                ExecutionContext context,
                                StationExecutionContext opContext,
                                StationLog record,
                                Exception exception) {

        // On publie l'évènement d'erreur quoi qu'il arrive
        if (context.getEventManager() != null) {
            context.getEventManager()
                    .publish(new OperationErrorEvent(
                            context.getPipelineId(),
                            context.getExecutionId(),
                            station.getId(),
                            input,
                            exception
                    ));
        }

        // Aucune stratégie onError : comportement par défaut
        if (station.getOnErrors() == null || station.getOnErrors().isEmpty()) {
            record.markFailed(exception);
            return null;
        }

        BaseError matched = null;

        for (BaseError error : (List<BaseError>) station.getOnErrors()) {
            if (error == null) {
                continue;
            }

            // Filtre sur le type de throwable
            Class<? extends Throwable> t = error.getThrowableType();
            if (t != null && !t.isAssignableFrom(exception.getClass())) {
                continue;
            }

            // Filtre sur la condition (si présente)
            Condition cond = error.getCondition();
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
            case IGNORE -> handleSkippedByCondition(station, input, opContext, record);
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
    protected List<Throwable> buildErrorListForRelease(StationLog record,
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
     * <p>
     * Appelé AVANT les preProcessors et AVANT la logique métier.
     * C'est ici que les sous-classes peuvent acquérir des ressources (locks, etc.).
     */
    protected void setUp(S station, Object input, StationExecutionContext operationExecution) {
        // no-op par défaut
    }

    /**
     * Hook de libération par exécution.
     * <p>
     * Appelé APRÈS les postProcessors, dans le bloc finally.
     * Reçoit éventuellement le résultat et/ou l'erreur si une exception a été levée.
     */
    protected void release(S station, Object result, StationExecutionContext context, List<Throwable> errors) {
        // no-op par défaut
    }

    /**
     * Implémentation réelle de l'opération métier.
     * Les classes concrètes manipulent librement le contexte
     * (ex : ajout de capabilities) avant de renvoyer le résultat.
     */
    protected abstract Object doExecute(S station,
                                        Object input,
                                        StationRunner runner,
                                        StationExecutionContext opContext) throws Exception;
}