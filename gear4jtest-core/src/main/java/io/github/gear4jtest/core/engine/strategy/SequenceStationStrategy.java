package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class SequenceStationStrategy extends AbstractStationStrategy<SequenceStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return SequenceStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object doExecute(SequenceStation station, Object input, StationRunner runner, StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());

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
                case MARK_AND_PROCEED -> collectedErrors.add(
                        FlowStrategySupport.representativeThrowable(
                                childLog,
                                "Step failed without exception: " + child.getId()));
                case INTERRUPT -> {
                    StationLog parentLog = operationExecution.getRecord();
                    FlowStrategySupport.applyInterruptToParentLog(parentLog, childLog, config);
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
}
