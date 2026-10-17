package io.github.gear4jtest.studio.runtime;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;

import static io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.*;

/**
 * Binds already admitted Studio XML parameters without rewriting the stored
 * artifact.
 */
final class ModeBindings {
    private ModeBindings() {
    }

    static XmlAssemblyLineDefinition bind(XmlAssemblyLineDefinition source,
                                          OperationCatalog catalog,
                                          ExecutionMode mode) {
        return new XmlAssemblyLineDefinition(source.id(), source.inputType(), source.outputType(),
                source.operations().stream().map(op -> bind(op, catalog, mode)).toList(),
                source.configuration(), source.dependencies());
    }

    private static Operation bind(Operation operation, OperationCatalog catalog, ExecutionMode mode) {
        if (operation instanceof ProcessingOperation processing)
            return bind(processing, catalog, mode);
        if (operation instanceof IfElseOperation choice)
            return new IfElseOperation(choice.id(), choice.inputType(), choice.outputType(),
                    choice.conditionalOperations().stream().map(branch -> new ConditionalOperation(branch.id(),
                            branch.condition(), bind(branch.operation(), catalog, mode))).toList(),
                    choice.elseOperation() == null ? null : bind(choice.elseOperation(), catalog, mode));
        throw new SecurityException("Unsupported Studio operation");
    }

    private static ProcessingOperation bind(ProcessingOperation operation,
                                            OperationCatalog catalog,
                                            ExecutionMode mode) {
        var logical = catalog.find(operation.type());
        var binding = logical == null ? null : logical.bindings().get(mode);
        if (binding == null)
            throw new SecurityException("Operation unavailable in requested mode");
        Parameters parameters = null;
        if (operation.parameters() != null) {
            var values = operation.parameters().parameters().stream().map(parameter -> {
                if (!(parameter instanceof ValueParameter value))
                    throw new SecurityException("Unsupported Studio parameter");
                return (Parameter) new ValueParameter(binding.retriever(logical.parameter(value.retriever())),
                        value.value(), value.valueType());
            }).toList();
            parameters = new Parameters(values);
        }
        return new ProcessingOperation(operation.id(), operation.type(), operation.inputType(), parameters,
                operation.errorHandlers(), operation.conditions(), operation.fallbackTransformer());
    }
}
