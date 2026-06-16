package io.github.gear4jtest.xml.generator;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;

final class XmlGelUsageAnalyzer {
    private XmlGelUsageAnalyzer() {
    }

    static boolean usesGel(XmlPipelineDefinition definition) {
        for (Operation operation : definition.operations()) {
            if (usesGel(operation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesGel(Operation operation) {
        if (operation instanceof ProcessingOperation processingOperation) {
            return processingOperation.conditions().stream().anyMatch(Condition::isGel)
                    || processingOperation.errorHandlers().stream()
                            .anyMatch(handler -> handler.condition() != null && handler.condition().isGel());
        }
        if (operation instanceof IteratorOperation iteratorOperation) {
            return usesGel(iteratorOperation.operation());
        }
        if (operation instanceof ContainerOperation containerOperation) {
            return containerOperation.subLines().stream()
                    .anyMatch(subLine -> (subLine.condition() != null && subLine.condition().isGel())
                            || usesGel(subLine.operation()));
        }
        if (operation instanceof IfElseOperation ifElseOperation) {
            return ifElseOperation.conditionalOperations().stream()
                    .anyMatch(conditionalOperation -> conditionalOperation.condition().isGel()
                            || usesGel(conditionalOperation.operation()))
                    || (ifElseOperation.elseOperation() != null && usesGel(ifElseOperation.elseOperation()));
        }
        if (operation instanceof SignalOperation signalOperation) {
            return signalOperation.condition() != null && signalOperation.condition().isGel();
        }
        return false;
    }
}
