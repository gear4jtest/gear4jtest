package io.test.gear4jtest.xml.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.test.gear4jtest.external.api.translator.OperationChainTranslator;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Action;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContextParameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ErrorHandler;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Parameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SupplierParameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ValueParameter;

public final class XmlToJavaGenerator {

    public static final String DEFAULT_PACKAGE = "io.test.gear4jtest.xml.generated";

    private final String packageName;

    public XmlToJavaGenerator() {
        this(DEFAULT_PACKAGE);
    }

    public XmlToJavaGenerator(String packageName) {
        this.packageName = Objects.requireNonNull(packageName, "packageName");
    }

    public OperationChainTranslator.GenerationResult generate(XmlPipelineDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        String simpleClassName = toTypeName(definition.id()) + "Line";
        String fqcn = packageName + "." + simpleClassName;

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append("/** Generated from a Gear4J XML pipeline definition. */\n");
        code.append("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n");
        code.append("public final class ").append(simpleClassName)
                .append(" implements io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine {\n\n");

        appendDependencies(code, definition);
        appendConstructor(code, simpleClassName);

        List<String> emittedMethods = new ArrayList<>();
        for (Operation operation : definition.operations()) {
            appendOperationMethod(code, operation, emittedMethods);
        }

        appendConfigurationMethod(code, definition);
        appendAssemblyMethod(code, definition);
        code.append("}\n");

        return new OperationChainTranslator.GenerationResult(fqcn, JdtFormatter.format(code.toString()));
    }

    private void appendDependencies(StringBuilder code, XmlPipelineDefinition definition) {
        for (XmlPipelineDefinition.Dependency dependency : definition.dependencies()) {
            code.append("    @io.test.gear4jtest.external.api.loader.Inject(\"")
                    .append(escapeJava(dependency.name()))
                    .append("\")\n");
            code.append("    private ").append(requiredType(dependency.type())).append(" ").append(toFieldName(dependency.name())).append(";\n\n");
        }
    }

    private void appendConstructor(StringBuilder code, String simpleClassName) {
        code.append("    public ").append(simpleClassName).append("() {\n");
        code.append("    }\n\n");
    }

    private void appendAssemblyMethod(StringBuilder code, XmlPipelineDefinition definition) {
        code.append("    @Override\n");
        code.append("    public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {\n");
        code.append("        return (io.github.gear4jtest.core.api.AssemblyLine) io.github.gear4jtest.core.api.util.ElementModelBuilders\n");
        code.append("                .createAssemblyLine(\"").append(escapeJava(definition.id())).append("\")\n");

        for (Operation operation : definition.operations()) {
            code.append("                .then((io.github.gear4jtest.core.api.station.AbstractStation) ")
                    .append(methodName(operation)).append("())\n");
        }

        if (definition.configuration() != null) {
            code.append("                .configuration(createConfiguration())\n");
        }

        code.append("                .build();\n");
        code.append("    }\n\n");
    }

    private void appendConfigurationMethod(StringBuilder code, XmlPipelineDefinition definition) {
        if (definition.configuration() == null) {
            return;
        }

        code.append("    private io.github.gear4jtest.core.api.AssemblyLine.Configuration createConfiguration() {\n");
        code.append("        io.github.gear4jtest.core.api.AssemblyLine.Configuration.Builder builder =\n");
        code.append("                io.github.gear4jtest.core.api.AssemblyLine.Configuration.builder();\n");

        if (definition.configuration().eventHandling() != null) {
            boolean eventOnParameterChanged = Boolean.TRUE.equals(definition.configuration().eventHandling().eventOnParameterChanged());
            code.append("        builder.eventHandling(io.github.gear4jtest.core.api.config.EventHandlingDefinition.builder()\n");
            code.append("                .globalEventConfiguration(io.github.gear4jtest.core.api.config.EventHandlingDefinition.EventConfiguration.builder()\n");
            code.append("                        .eventOnParameterChanged(").append(eventOnParameterChanged).append(")\n");
            code.append("                        .build())\n");
            code.append("                .build());\n");
        }

        if (definition.configuration().persistence() != null) {
            boolean storeResultObject = !Boolean.FALSE.equals(definition.configuration().persistence().storeResultObject());
            code.append("        builder.persistence(io.github.gear4jtest.core.api.config.PersistenceConfiguration.builder()\n");
            code.append("                .storeResultObject(").append(storeResultObject).append(")\n");
            code.append("                .build());\n");
        }

        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }

    private void appendOperationMethod(StringBuilder code, Operation operation, List<String> emittedMethods) {
        String methodName = methodName(operation);
        if (emittedMethods.contains(methodName)) {
            return;
        }
        emittedMethods.add(methodName);

        if (operation instanceof ProcessingOperation processingOperation) {
            appendProcessingMethod(code, processingOperation);
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            appendIteratorMethod(code, iteratorOperation, emittedMethods);
        } else if (operation instanceof ContainerOperation containerOperation) {
            appendContainerMethod(code, containerOperation, emittedMethods);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            appendIfElseMethod(code, ifElseOperation, emittedMethods);
        } else if (operation instanceof SignalOperation signalOperation) {
            appendSignalMethod(code, signalOperation);
        } else {
            throw new IllegalArgumentException("Unsupported operation type: " + operation);
        }
    }

    private void appendProcessingMethod(StringBuilder code, ProcessingOperation operation) {
        code.append("    private io.github.gear4jtest.core.api.station.WorkStation ")
                .append(methodName(operation)).append("() {\n");
        code.append("        var builder =\n");
        code.append("                io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation(\"")
                .append(escapeJava(operation.id())).append("\", ").append(requiredType(operation.type())).append(".class);\n");

        for (Parameter parameter : operation.parameters().parameters()) {
            if (parameter instanceof ValueParameter valueParameter) {
                code.append("        builder.parameter(").append(valueParameter.retriever()).append(", ")
                        .append(valueExpression(valueParameter)).append(");\n");
            } else if (parameter instanceof SupplierParameter supplierParameter) {
                code.append("        builder.parameter(").append(supplierParameter.retriever()).append(", ")
                        .append(supplierParameter.supplier()).append(");\n");
            } else if (parameter instanceof ContextParameter contextParameter) {
                code.append("        builder.parameter(").append(contextParameter.retriever()).append(", ")
                        .append(contextParameter.function()).append(");\n");
            }
        }

        for (ErrorHandler errorHandler : operation.errorHandlers()) {
            appendErrorHandler(code, errorHandler);
        }

        for (Condition condition : operation.conditions()) {
            code.append("        builder.skipIf(").append(conditionLambda(condition)).append(");\n");
        }

        if (operation.fallbackTransformer() != null) {
            code.append("        builder.fallback((input, ctx) -> ")
                    .append(operation.fallbackTransformer().expression()).append(");\n");
        }

        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }

    private void appendErrorHandler(StringBuilder code, ErrorHandler errorHandler) {
        String builder = switch (errorHandler.signalType().toUpperCase(Locale.ROOT)) {
            case "FATAL" -> "fatal";
            case "STOP" -> "stop";
            case "IGNORE" -> "ignore";
            default -> throw new IllegalArgumentException("Unsupported error signal type: " + errorHandler.signalType());
        };

        String baseType = errorHandler.safe()
                ? "io.github.gear4jtest.core.api.behavior.BaseError.SafeError"
                : "io.github.gear4jtest.core.api.behavior.BaseError.UnSafeError";

        code.append("        builder.onError((").append(baseType).append(") io.github.gear4jtest.core.api.util.ElementModelBuilders.")
                .append(builder).append("(").append(requiredType(errorHandler.throwableType())).append(".class)\n");

        if (errorHandler.condition() != null) {
            code.append("                .condition(").append(conditionLambda(errorHandler.condition())).append(")\n");
        }

        if (errorHandler.action() != null) {
            code.append("                .action(() -> { ").append(actionStatement(errorHandler.action())).append(" })\n");
        }

        code.append("                .build());\n");
    }

    private void appendIteratorMethod(StringBuilder code, IteratorOperation operation, List<String> emittedMethods) {
        appendOperationMethod(code, operation.operation(), emittedMethods);

        code.append("    private io.github.gear4jtest.core.api.station.IteratorStation ")
                .append(methodName(operation)).append("() {\n");
        code.append("        return io.github.gear4jtest.core.api.util.ElementModelBuilders.iterate(\"")
                .append(escapeJava(operation.id())).append("\")\n");
        code.append("                .iterableFunction(").append(operation.iterableFunction()).append(")\n");
        code.append("                .pipeline(io.github.gear4jtest.core.api.util.ElementModelBuilders.chain(\"")
                .append(escapeJava(operation.id())).append(":chain\", (io.github.gear4jtest.core.api.station.AbstractStation) ")
                .append(methodName(operation.operation())).append("()).build())\n");

        if (operation.accumulator() != null) {
            String accumulator = switch (operation.accumulator().toUpperCase(Locale.ROOT)) {
                case "LIST" -> "io.github.gear4jtest.core.api.util.ElementModelBuilders.toList()";
                case "SET" -> "io.github.gear4jtest.core.api.util.ElementModelBuilders.toSet()";
                default -> throw new IllegalArgumentException("Unsupported accumulator: " + operation.accumulator());
            };
            code.append("                .accumulator(").append(accumulator).append(")\n");
        } else if (operation.collector() != null) {
            code.append("                .collector(").append(operation.collector()).append(")\n");
        }

        code.append("                .build();\n");
        code.append("    }\n\n");
    }

    private void appendContainerMethod(StringBuilder code, ContainerOperation operation, List<String> emittedMethods) {
        for (SubLine subLine : operation.subLines()) {
            appendOperationMethod(code, subLine.operation(), emittedMethods);
        }

        code.append("    private io.github.gear4jtest.core.api.station.ContainerBaseStation ")
                .append(methodName(operation)).append("() {\n");

        if (operation.parallel()) {
            if (operation.threadPoolSize() != null) {
                code.append("        return io.github.gear4jtest.core.api.util.ElementModelBuilders.container(")
                        .append(requiredType(operation.inputType())).append(".class, java.util.concurrent.Executors.newFixedThreadPool(")
                        .append(operation.threadPoolSize()).append("))\n");
            } else {
                code.append("        return io.github.gear4jtest.core.api.util.ElementModelBuilders.container(")
                        .append(requiredType(operation.inputType())).append(".class, java.util.concurrent.Executors.newCachedThreadPool())\n");
            }
        } else {
            code.append("        return io.github.gear4jtest.core.api.util.ElementModelBuilders.container(")
                    .append(requiredType(operation.inputType())).append(".class)\n");
        }

        for (SubLine subLine : operation.subLines()) {
            String subLineId = subLine.id() == null || subLine.id().isBlank() ? subLine.operation().id() : subLine.id();
            code.append("                .withSubLine(\"").append(escapeJava(subLineId)).append("\", ")
                    .append("(io.github.gear4jtest.core.api.station.AbstractStation) ")
                    .append(methodName(subLine.operation())).append("()");
            if (subLine.condition() != null) {
                code.append(", ").append(conditionLambda(subLine.condition()));
            }
            code.append(")\n");
        }

        if (operation.returnsFunction() != null) {
            code.append("                .returns(").append(operation.returnsFunction()).append(");\n");
        } else {
            code.append("                .build();\n");
        }

        code.append("    }\n\n");
    }

    private void appendIfElseMethod(StringBuilder code, IfElseOperation operation, List<String> emittedMethods) {
        for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            appendOperationMethod(code, conditionalOperation.operation(), emittedMethods);
        }
        if (operation.elseOperation() != null) {
            appendOperationMethod(code, operation.elseOperation(), emittedMethods);
        }

        code.append("    private io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation ")
                .append(methodName(operation)).append("() {\n");
        code.append("        return io.github.gear4jtest.core.api.util.ElementModelBuilders.ifElseContainer(")
                .append(requiredType(operation.inputType())).append(".class)\n");

        for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            code.append("                .conditionally((io.github.gear4jtest.core.api.station.AbstractStation) ")
                    .append(methodName(conditionalOperation.operation())).append("(), ")
                    .append(conditionLambda(conditionalOperation.condition())).append(")\n");
        }

        if (operation.elseOperation() == null) {
            throw new IllegalArgumentException("ifElseContainer requires an elseOperation because the current core builder has no build() method");
        }

        code.append("                .elseOp((io.github.gear4jtest.core.api.station.AbstractStation) ")
                .append(methodName(operation.elseOperation())).append("());\n");
        code.append("    }\n\n");
    }

    private void appendSignalMethod(StringBuilder code, SignalOperation operation) {
        code.append("    private io.github.gear4jtest.core.api.station.SignalStation ")
                .append(methodName(operation)).append("() {\n");
        code.append("        io.github.gear4jtest.core.api.station.SignalStation.Builder builder =\n");
        code.append("                new io.github.gear4jtest.core.api.station.SignalStation.Builder()\n");
        code.append("                        .id(\"").append(escapeJava(operation.id())).append("\")\n");
        code.append("                        .type(io.github.gear4jtest.core.api.behavior.SignalType.")
                .append(operation.type().toUpperCase(Locale.ROOT)).append(");\n");
        code.append("        builder.condition(")
                .append(signalConditionLambda(operation.condition(), operation.inputType()))
                .append(");\n");
        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }

    private static String valueExpression(ValueParameter parameter) {
        if ("java.lang.String".equals(parameter.valueType())) {
            return "\"" + escapeJava(parameter.value()) + "\"";
        }
        return parameter.value();
    }

    private static String conditionLambda(Condition condition) {
        String expression = condition.expression().trim();
        if (expression.contains("->")) {
            return expression;
        }
        return "(input, ctx) -> " + expression;
    }

    private static String signalConditionLambda(Condition condition, String inputType) {
        if (condition == null) {
            return "sig -> true";
        }

        String expression = condition.expression().trim();
        if (expression.contains("->")) {
            return expression;
        }

        String type = requiredType(inputType);
        return "sig -> { "
                + type + " input = (" + type + ") sig.getItem(); "
                + "var ctx = sig.getItemExecution(); "
                + "return " + expression + "; "
                + "}";
    }

    private static String actionStatement(Action action) {
        String statement = action.expression().trim();
        return statement.endsWith(";") ? statement : statement + ";";
    }

    private static String methodName(Operation operation) {
        String prefix;
        if (operation instanceof ProcessingOperation) {
            prefix = "process";
        } else if (operation instanceof IteratorOperation) {
            prefix = "iterate";
        } else if (operation instanceof ContainerOperation) {
            prefix = "container";
        } else if (operation instanceof IfElseOperation) {
            prefix = "ifElse";
        } else if (operation instanceof SignalOperation) {
            prefix = "signal";
        } else {
            throw new IllegalArgumentException("Unsupported operation type: " + operation);
        }
        return prefix + toTypeName(operation.id());
    }

    private static String requiredType(String type) {
        if (type == null || type.isBlank()) {
            return "java.lang.Object";
        }
        return type.trim();
    }

    private static String toTypeName(String value) {
        String identifier = toJavaIdentifier(value);
        return identifier.substring(0, 1).toUpperCase(Locale.ROOT) + identifier.substring(1);
    }

    private static String toFieldName(String value) {
        String identifier = toJavaIdentifier(value);
        return Character.toLowerCase(identifier.charAt(0)) + identifier.substring(1);
    }

    private static String toJavaIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "X";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            builder.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        if (!Character.isJavaIdentifierStart(builder.charAt(0))) {
            builder.insert(0, '_');
        }
        return builder.toString();
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
