package io.github.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;

/**
 * Collects generated executor dependencies required by parallel XML containers.
 */
final class XmlParallelExecutorDependencies {
    private XmlParallelExecutorDependencies() {
    }

    static Map<ContainerOperation, String> collect(XmlAssemblyLineDefinition definition) {
        Map<ContainerOperation, String> executorFields = new LinkedHashMap<>();
        Map<String, ContainerOperation> ownersByField = new LinkedHashMap<>();

        for (Operation operation : definition.operations()) {
            collect(operation, executorFields, ownersByField);
        }

        return new LinkedHashMap<>(executorFields);
    }

    private static void collect(Operation operation,
                                Map<ContainerOperation, String> executorFields,
                                Map<String, ContainerOperation> ownersByField) {
        if (operation instanceof ContainerOperation containerOperation) {
            if (containerOperation.parallel()) {
                String fieldName = XmlGeneratedNames.parallelExecutorFieldName(containerOperation);
                ContainerOperation previousOwner = ownersByField.putIfAbsent(fieldName, containerOperation);
                if (previousOwner != null && !previousOwner.equals(containerOperation)) {
                    throw new IllegalArgumentException("Parallel XML containers '" + previousOwner.id() + "' and '"
                            + containerOperation.id() + "' generate the same executor field '" + fieldName
                            + "'. Use ids that remain unique after Java identifier normalization.");
                }
                executorFields.putIfAbsent(containerOperation, fieldName);
            }
            for (SubLine subLine : containerOperation.subLines()) {
                collect(subLine.operation(), executorFields, ownersByField);
            }
            return;
        }

        if (operation instanceof IteratorOperation iteratorOperation) {
            collect(iteratorOperation.operation(), executorFields, ownersByField);
            return;
        }

        if (operation instanceof IfElseOperation ifElseOperation) {
            for (XmlAssemblyLineDefinition.ConditionalOperation conditionalOperation : ifElseOperation
                    .conditionalOperations()) {
                collect(conditionalOperation.operation(), executorFields, ownersByField);
            }
            if (ifElseOperation.elseOperation() != null) {
                collect(ifElseOperation.elseOperation(), executorFields, ownersByField);
            }
        }
    }
}
