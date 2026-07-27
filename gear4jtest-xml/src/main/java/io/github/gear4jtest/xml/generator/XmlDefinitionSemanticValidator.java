package io.github.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.SourceVersion;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Dependency;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ErrorHandler;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ValueParameter;
import io.github.gear4jtest.xml.validator.XmlDefinitionValidationException;

/**
 * Validates model constraints that XML Schema cannot express and that must hold
 * before rendering Java source.
 */
final class XmlDefinitionSemanticValidator {
    private static final String ROOT_PATH = "/assemblyLine";

    private final Map<String, GeneratedNameOwner> generatedFields = new LinkedHashMap<>();
    private final Map<String, GeneratedMethodOwner> generatedMethods = new LinkedHashMap<>();

    private XmlDefinitionSemanticValidator() {
    }

    static void validate(XmlAssemblyLineDefinition definition) {
        new XmlDefinitionSemanticValidator().validateDefinition(Objects.requireNonNull(definition, "definition"));
    }

    private void validateDefinition(XmlAssemblyLineDefinition definition) {
        requireText(ROOT_PATH + "/@id", definition.id(), "assembly-line id must not be blank");
        requireGeneratedIdentifier(ROOT_PATH + "/@id", definition.id(),
                                   XmlGeneratedNames.toTypeName(definition.id()) + "Line", "generated class");
        requireType(ROOT_PATH + "/@inputType", definition.inputType(), true);
        requireType(ROOT_PATH + "/@outputType", definition.outputType(), false);

        validateDependencies(requireList(ROOT_PATH + "/dependencies", definition.dependencies()));
        validateOperations(requireList(ROOT_PATH + "/operations", definition.operations()),
                           ROOT_PATH + "/operations");
    }

    private void validateDependencies(List<Dependency> dependencies) {
        for (int index = 0; index < dependencies.size(); index++) {
            String path = ROOT_PATH + "/dependencies/dependency[" + (index + 1) + "]";
            Dependency dependency = requireValue(path, dependencies.get(index));
            String namePath = path + "/@name";
            requireText(namePath, dependency.name(), "dependency name must not be blank");

            String fieldName = XmlGeneratedNames.toFieldName(dependency.name());
            requireGeneratedIdentifier(namePath, dependency.name(), fieldName, "dependency field");
            registerGeneratedName(generatedFields, fieldName, namePath, dependency.name(),
                                  "Generated field name collision");
            requireType(path + "/@type", dependency.type(), true);
        }
    }

    private void validateOperations(List<Operation> operations, String parentPath) {
        Map<String, Integer> countsByElement = new LinkedHashMap<>();
        for (Operation operation : operations) {
            Operation requiredOperation = requireValue(parentPath, operation);
            String elementName = elementName(requiredOperation);
            int index = countsByElement.merge(elementName, 1, Integer::sum);
            validateOperation(requiredOperation, parentPath + "/" + elementName + "[" + index + "]");
        }
    }

    private void validateOperation(Operation operation, String path) {
        String idPath = path + "/@id";
        requireText(idPath, operation.id(), "operation id must not be blank");
        String methodName = XmlGeneratedNames.operationMethodName(operation);
        requireGeneratedIdentifier(idPath, operation.id(), methodName, "operation method");
        registerGeneratedMethod(methodName, idPath, operation);

        if (operation instanceof ProcessingOperation processingOperation) {
            validateProcessing(processingOperation, path);
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            validateIterator(iteratorOperation, path);
        } else if (operation instanceof ContainerOperation containerOperation) {
            validateContainer(containerOperation, path);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            validateIfElse(ifElseOperation, path);
        } else if (operation instanceof SignalOperation signalOperation) {
            requireType(path + "/@inputType", signalOperation.inputType(), false);
        } else {
            throw invalid(path, operation.getClass().getName(), "unsupported operation type");
        }
    }

    private void validateProcessing(ProcessingOperation operation, String path) {
        requireType(path + "/@type", operation.type(), true);
        requireType(path + "/@inputType", operation.inputType(), false);

        List<Parameter> parameters = operation.parameters() == null
                ? List.of()
                : requireList(path + "/parameters", operation.parameters().parameters());
        for (int index = 0; index < parameters.size(); index++) {
            Parameter parameter = requireValue(path + "/parameters", parameters.get(index));
            if (parameter instanceof ValueParameter valueParameter) {
                requireType(path + "/parameters/*[" + (index + 1) + "]/@valueType",
                            valueParameter.valueType(), true);
            }
        }

        List<ErrorHandler> errorHandlers = requireList(path + "/onErrors", operation.errorHandlers());
        for (int index = 0; index < errorHandlers.size(); index++) {
            ErrorHandler errorHandler = requireValue(path + "/onErrors", errorHandlers.get(index));
            requireType(path + "/onErrors/*[" + (index + 1) + "]/@throwableType",
                        errorHandler.throwableType(), true);
        }

        if (operation.fallbackTransformer() != null) {
            requireType(path + "/fallbackTransformer/@inputType",
                        operation.fallbackTransformer().inputType(), false);
            requireType(path + "/fallbackTransformer/@outputType",
                        operation.fallbackTransformer().outputType(), false);
        }
    }

    private void validateIterator(IteratorOperation operation, String path) {
        requireType(path + "/@inputType", operation.inputType(), false);
        requireType(path + "/@outputType", operation.outputType(), false);
        Operation child = requireValue(path + "/operation", operation.operation());
        validateOperation(child, path + "/operation/" + elementName(child) + "[1]");
    }

    private void validateContainer(ContainerOperation operation, String path) {
        requireType(path + "/@inputType", operation.inputType(), true);
        requireType(path + "/@outputType", operation.outputType(), true);
        if (operation.parallel()) {
            String fieldName = XmlGeneratedNames.parallelExecutorFieldName(operation);
            requireGeneratedIdentifier(path + "/@id", operation.id(), fieldName, "parallel executor field");
            registerGeneratedName(generatedFields, fieldName, path + "/@id", operation.id(),
                                  "Generated field name collision");
        }

        List<SubLine> subLines = requireList(path + "/subLines", operation.subLines());
        Map<String, GeneratedNameOwner> branchIds = new LinkedHashMap<>();
        for (int index = 0; index < subLines.size(); index++) {
            String subLinePath = path + "/subLines/subLine[" + (index + 1) + "]";
            SubLine subLine = requireValue(subLinePath, subLines.get(index));
            requireText(subLinePath + "/@id", subLine.id(), "container branch id must not be blank");
            registerExactIdentifier(branchIds, subLine.id(), subLinePath + "/@id",
                                    "Duplicate container branch id");
            Operation child = requireValue(subLinePath, subLine.operation());
            validateOperation(child, subLinePath + "/" + elementName(child) + "[1]");
        }
    }

    private void validateIfElse(IfElseOperation operation, String path) {
        requireType(path + "/@inputType", operation.inputType(), true);
        requireType(path + "/@outputType", operation.outputType(), true);

        List<ConditionalOperation> conditionalOperations = requireList(path + "/conditionalOperations",
                                                                       operation.conditionalOperations());
        Map<String, GeneratedNameOwner> branchIds = new LinkedHashMap<>();
        for (int index = 0; index < conditionalOperations.size(); index++) {
            String conditionalPath = path + "/conditionalOperations/conditionalOperation[" + (index + 1) + "]";
            ConditionalOperation conditional = requireValue(conditionalPath, conditionalOperations.get(index));
            requireText(conditionalPath + "/@id", conditional.id(), "conditional branch id must not be blank");
            registerExactIdentifier(branchIds, conditional.id(), conditionalPath + "/@id",
                                    "Duplicate if/else branch id");
            ProcessingOperation child = requireValue(conditionalPath, conditional.operation());
            validateOperation(child, conditionalPath + "/processingOperation[1]");
        }

        if (operation.elseOperation() != null) {
            registerExactIdentifier(branchIds, operation.elseOperation().id(), path + "/elseOperation/@id",
                                    "Duplicate if/else branch id");
            validateOperation(operation.elseOperation(), path + "/elseOperation");
        }
    }

    private void requireType(String path, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw invalid(path, value, "Java type must not be blank");
            }
            return;
        }
        try {
            JavaTypeName.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new XmlDefinitionValidationException(path, value, "invalid Java type", exception);
        }
    }

    private void requireGeneratedIdentifier(String path,
                                            String sourceValue,
                                            String generatedName,
                                            String generatedMemberKind) {
        if (!SourceVersion.isIdentifier(generatedName)
                || SourceVersion.isKeyword(generatedName, SourceVersion.RELEASE_17)) {
            throw invalid(path, sourceValue,
                          generatedMemberKind + " name '" + generatedName + "' is not a valid Java 17 identifier");
        }
    }

    private void registerGeneratedName(Map<String, GeneratedNameOwner> owners,
                                       String generatedName,
                                       String path,
                                       String sourceValue,
                                       String reason) {
        GeneratedNameOwner previous = owners.putIfAbsent(generatedName, new GeneratedNameOwner(path, sourceValue));
        if (previous != null) {
            throw invalid(path, sourceValue, reason + " '" + generatedName + "' with " + previous.path()
                    + " (value '" + previous.sourceValue() + "')");
        }
    }

    private void registerGeneratedMethod(String methodName, String path, Operation operation) {
        GeneratedMethodOwner previous = generatedMethods.putIfAbsent(methodName,
                                                                     new GeneratedMethodOwner(path, operation));
        if (previous != null && !previous.operation().equals(operation)) {
            throw invalid(path, operation.id(), "Generated method name collision for method '" + methodName
                    + "' with " + previous.path() + " (value '" + previous.operation().id() + "')");
        }
    }

    private void registerExactIdentifier(Map<String, GeneratedNameOwner> owners,
                                         String identifier,
                                         String path,
                                         String reason) {
        GeneratedNameOwner previous = owners.putIfAbsent(identifier, new GeneratedNameOwner(path, identifier));
        if (previous != null) {
            throw invalid(path, identifier, reason + " also declared at " + previous.path());
        }
    }

    private static String elementName(Operation operation) {
        if (operation instanceof ProcessingOperation) {
            return "processingOperation";
        }
        if (operation instanceof IteratorOperation) {
            return "iterator";
        }
        if (operation instanceof ContainerOperation) {
            return "container";
        }
        if (operation instanceof IfElseOperation) {
            return "ifElseContainer";
        }
        if (operation instanceof SignalOperation) {
            return "signal";
        }
        throw new IllegalArgumentException("Unsupported operation type: " + operation);
    }

    private static void requireText(String path, String value, String reason) {
        if (value == null || value.isBlank()) {
            throw invalid(path, value, reason);
        }
    }

    private static <T> T requireValue(String path, T value) {
        if (value == null) {
            throw invalid(path, null, "value must not be null");
        }
        return value;
    }

    private static <T> List<T> requireList(String path, List<T> values) {
        if (values == null) {
            throw invalid(path, null, "list must not be null");
        }
        return values;
    }

    private static XmlDefinitionValidationException invalid(String path, String value, String reason) {
        return new XmlDefinitionValidationException(path, value, reason);
    }

    private record GeneratedNameOwner(String path, String sourceValue) {}

    private record GeneratedMethodOwner(String path, Operation operation) {}
}
