package io.github.gear4jtest.xml.generator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;

final class XmlOperatorCapabilityResolver {
    private final XmlOperatorCapabilityPolicy policy;
    private final ExecutionMode mode;
    private final Map<Operation, Operation> resolvedOperations = new IdentityHashMap<>();

    private XmlOperatorCapabilityResolver(XmlOperatorCapabilityPolicy policy, ExecutionMode mode) {
        this.policy = Objects.requireNonNull(policy, "operatorCapabilityPolicy must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    static XmlAssemblyLineDefinition resolve(XmlAssemblyLineDefinition definition,
                                             XmlOperatorCapabilityPolicy policy,
                                             ExecutionMode mode) {
        Objects.requireNonNull(definition, "definition must not be null");
        XmlOperatorCapabilityResolver resolver = new XmlOperatorCapabilityResolver(policy, mode);
        List<Operation> operations = definition.operations().stream()
                .map(resolver::resolveOperation)
                .toList();
        return new XmlAssemblyLineDefinition(definition.id(), definition.inputType(), definition.outputType(),
                operations, definition.configuration(), definition.dependencies());
    }

    private Operation resolveOperation(Operation operation) {
        Operation existing = resolvedOperations.get(operation);
        if (existing != null) {
            return existing;
        }

        Operation resolved;
        if (operation instanceof ProcessingOperation processingOperation) {
            resolved = resolveProcessing(processingOperation);
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            resolved = new IteratorOperation(iteratorOperation.id(), iteratorOperation.inputType(),
                    iteratorOperation.outputType(), iteratorOperation.iterableFunction(),
                    resolveOperation(iteratorOperation.operation()), iteratorOperation.accumulator(),
                    iteratorOperation.collector());
        } else if (operation instanceof ContainerOperation containerOperation) {
            resolved = resolveContainer(containerOperation);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            resolved = resolveIfElse(ifElseOperation);
        } else {
            resolved = operation;
        }
        resolvedOperations.put(operation, resolved);
        return resolved;
    }

    private ProcessingOperation resolveProcessing(ProcessingOperation operation) {
        return new ProcessingOperation(operation.id(), policy.resolve(operation.type(), mode), operation.inputType(),
                operation.parameters(), operation.errorHandlers(), operation.conditions(),
                operation.fallbackTransformer());
    }

    private ContainerOperation resolveContainer(ContainerOperation operation) {
        List<SubLine> subLines = operation.subLines().stream()
                .map(subLine -> new SubLine(subLine.id(), subLine.condition(),
                        resolveOperation(subLine.operation())))
                .toList();
        return new ContainerOperation(operation.id(), operation.inputType(), operation.outputType(),
                operation.parallel(), operation.threadPoolSize(), subLines, operation.returnsFunction());
    }

    private IfElseOperation resolveIfElse(IfElseOperation operation) {
        List<ConditionalOperation> conditionalOperations = new ArrayList<>();
        for (ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            conditionalOperations.add(new ConditionalOperation(conditionalOperation.id(),
                    conditionalOperation.condition(),
                    (ProcessingOperation) resolveOperation(conditionalOperation.operation())));
        }
        ProcessingOperation elseOperation = operation.elseOperation() == null ? null
                : (ProcessingOperation) resolveOperation(operation.elseOperation());
        return new IfElseOperation(operation.id(), operation.inputType(), operation.outputType(),
                List.copyOf(conditionalOperations), elseOperation);
    }
}
