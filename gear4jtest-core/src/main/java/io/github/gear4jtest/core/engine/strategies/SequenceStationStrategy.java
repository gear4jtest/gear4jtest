package io.github.gear4jtest.core.engine.strategies;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.engine.flow.CancelPolicy;
import io.github.gear4jtest.core.engine.flow.FlowConfig;
import io.github.gear4jtest.core.engine.flow.FlowDecider;
import io.github.gear4jtest.core.engine.flow.FlowDecision;
import io.github.gear4jtest.core.engine.flow.StopPolicy;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.SequenceStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class SequenceStationStrategy extends AbstractStationStrategy<SequenceStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return SequenceStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doExecute(SequenceStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        FlowConfig config = resolveFlowConfig(station);

        Object currentInput = input;
        List<Throwable> collectedErrors = new ArrayList<>();

        for (AbstractStation<?, ?> child : (List<AbstractStation>) station.getSteps()) {
            StationLog childLog = runner.run(currentInput, child, operationExecution);

            FlowDecision decision = FlowDecider.decide(childLog, config);
            switch (decision) {
                case PROCEED -> {
                    // Règle d'or : on ne met à jour l'input QUE si l'étape a réellement réussi.
                    if (childLog.getStatus() == StationLog.Status.SUCCEEDED) {
                        currentInput = childLog.getOutput();
                    }
                }
                case MARK_AND_PROCEED -> {
                    // Collect & fail : on note l'erreur puis on continue.
                    // On conserve l'input précédent (comme un ignore).
                    if (childLog.getThrowables() != null && !childLog.getThrowables().isEmpty()) {
                        collectedErrors.addAll(childLog.getThrowables());
                    } else {
                        collectedErrors.add(new RuntimeException("Step failed without exception: " + child.getId()));
                    }
                }
                case INTERRUPT -> {
                    // STOP/FAIL/CANCEL propagé (ou STOP/CANCEL transformé en failure selon policy)
                    StationLog parentLog = operationExecution.getRecord();
                    applyInterruptToParentLog(parentLog, childLog, config);
                    // On conserve le dernier output valide comme output de la sequence.
                    parentLog.setOutput(currentInput);
                    return currentInput;
                }
            }
        }

        // Fin de boucle : si on a collecté des erreurs, la séquence échoue globalement.
        if (!collectedErrors.isEmpty()) {
            StationLog parentLog = operationExecution.getRecord();
            // Meilleur-effort : on prend la première comme représentante.
            Throwable first = collectedErrors.get(0);
            if (first instanceof Exception ex) {
                parentLog.markFailed(ex);
            } else {
                parentLog.markFailed(new RuntimeException(first.getMessage(), first));
            }
            // output = dernier output valide
            parentLog.setOutput(currentInput);
            return currentInput;
        }

        return currentInput;
    }

    private static FlowConfig resolveFlowConfig(SequenceStation<?, ?> station) {
        return station.getFlowConfig() != null ? station.getFlowConfig() : FlowConfig.DEFAULT;
    }

    private static void applyInterruptToParentLog(
            StationLog parent,
            StationLog child,
            FlowConfig config
    ) {
        StationLog.Status childStatus = child.getStatus();

        Exception representative = null;
        if (child.getThrowables() != null && !child.getThrowables().isEmpty()) {
            Throwable t = child.getThrowables().get(0);
            representative = (t instanceof Exception ex)
                    ? ex
                    : new RuntimeException(t.getMessage(), t);
        } else if (child.getErrorMessage() != null) {
            representative = new RuntimeException(child.getErrorMessage());
        }

        if (childStatus == StationLog.Status.FAILED) {
            parent.markFailed(representative);
            return;
        }

        if (childStatus == StationLog.Status.STOPPED) {
            if (config.stopPolicy() == StopPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markStopped(representative);
            }
            return;
        }

        if (childStatus == StationLog.Status.CANCELLED) {
            if (config.cancelPolicy() == CancelPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markCancelled(representative);
            }
            return;
        }

        // fallback sécurité
        parent.markFailed(representative != null ? representative : new RuntimeException("Unknown terminal status: " + childStatus));
    }
}
