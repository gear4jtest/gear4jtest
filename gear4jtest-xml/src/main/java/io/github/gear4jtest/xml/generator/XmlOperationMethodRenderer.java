package io.github.gear4jtest.xml.generator;

import java.util.Locale;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContextParameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ErrorHandler;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SupplierParameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ValueParameter;

final class XmlOperationMethodRenderer {
    private static final String INDENT_PRIVATE = "    private ";
    private static final String METHOD_OPEN = "() {\n";
    private static final String BUILDER_TYPE_PREFIX = ".Builder<";
    private static final String BUILDER_PARAMETER = "        builder.parameter(";
    private static final String METHOD_END = "    }\n\n";
    private static final String ELEMENT_MODEL_BUILDERS = "io.github.gear4jtest.core.api.util.ElementModelBuilders";

    void appendOperationMethod(StringBuilder code, Operation operation, XmlGenerationContext context) {
        String methodName = XmlGeneratedNames.operationMethodName(operation);
        Operation previousOperation = context.emittedMethods().putIfAbsent(methodName, operation);
        if (previousOperation != null) {
            if (previousOperation.equals(operation)) {
                return;
            }
            throw new IllegalArgumentException("Generated method name collision for method '" + methodName
                    + "' between operations '" + previousOperation.id() + "' and '" + operation.id()
                    + "'. Use ids that remain unique after Java identifier normalization.");
        }

        if (operation instanceof ProcessingOperation processingOperation) {
            appendProcessingMethod(code, processingOperation, context);
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            appendIteratorMethod(code, iteratorOperation, context);
        } else if (operation instanceof ContainerOperation containerOperation) {
            appendContainerMethod(code, containerOperation, context);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            appendIfElseMethod(code, ifElseOperation, context);
        } else if (operation instanceof SignalOperation signalOperation) {
            appendSignalMethod(code, signalOperation, context);
        } else {
            throw new IllegalArgumentException("Unsupported operation type: " + operation);
        }
    }

    private void appendProcessingMethod(StringBuilder code,
                                        ProcessingOperation operation,
                                        XmlGenerationContext context) {
        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        OperationSignature signature = context.signatures().get(operation);
        JavaTypeName operationType = JavaTypeName.parse(operation.type());
        String workStation = imports.use("io.github.gear4jtest.core.api.station.WorkStation");
        imports.addStatic(ELEMENT_MODEL_BUILDERS + ".processingOperation");

        code.append(INDENT_PRIVATE).append(workStation).append("<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append(METHOD_OPEN);
        code.append("        ").append(workStation).append(BUILDER_TYPE_PREFIX)
                .append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append(", ")
                .append(operationType.render(imports)).append("> builder = processingOperation(\"")
                .append(JavaStringEscaper.escapeJava(operation.id())).append("\", ")
                .append(operationType.renderClassLiteral(imports))
                .append(");\n");

        for (Parameter parameter : operation.parameters().parameters()) {
            if (parameter instanceof ValueParameter valueParameter) {
                code.append(BUILDER_PARAMETER)
                        .append(expressions.normalizeRetriever(valueParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.valueExpression(valueParameter)).append(");\n");
            } else if (parameter instanceof SupplierParameter supplierParameter) {
                code.append(BUILDER_PARAMETER)
                        .append(expressions.normalizeRetriever(supplierParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.normalizeExpression(supplierParameter.supplier(), imports)).append(");\n");
            } else if (parameter instanceof ContextParameter contextParameter) {
                code.append(BUILDER_PARAMETER)
                        .append(expressions.normalizeRetriever(contextParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.normalizeExpression(contextParameter.function(), imports)).append(");\n");
            }
        }

        for (ErrorHandler errorHandler : operation.errorHandlers()) {
            appendErrorHandler(code, errorHandler, signature.inputType(), context);
        }

        for (Condition condition : operation.conditions()) {
            code.append("        builder.skipIf(").append(expressions.conditionLambda(condition, imports))
                    .append(");\n");
        }

        if (operation.fallbackTransformer() != null) {
            code.append("        builder.fallback((input, ctx) -> ")
                    .append(expressions.normalizeExpression(operation.fallbackTransformer().expression(), imports))
                    .append(");\n");
        }

        code.append("        return builder.build();\n");
        code.append(METHOD_END);
    }

    private void appendErrorHandler(StringBuilder code,
                                    ErrorHandler errorHandler,
                                    JavaTypeName inputType,
                                    XmlGenerationContext context) {
        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        String builder = switch (errorHandler.signalType().toUpperCase(Locale.ROOT)) {
            case "FATAL" -> "fatal";
            case "STOP" -> "stop";
            case "IGNORE" -> "ignore";
            default ->
                throw new IllegalArgumentException("Unsupported error signal type: " + errorHandler.signalType());
        };

        if (!"IGNORE".equals(errorHandler.signalType().toUpperCase(Locale.ROOT))) {
            imports.addStatic(ELEMENT_MODEL_BUILDERS + "." + builder);
        }

        String elementModelBuilders = imports.use(ELEMENT_MODEL_BUILDERS);
        code.append("        builder.onError(").append(elementModelBuilders).append(".<")
                .append(inputType.render(imports)).append(">").append(builder).append("(")
                .append(JavaTypeName.parse(errorHandler.throwableType()).renderClassLiteral(imports)).append(")\n");

        if (errorHandler.condition() != null) {
            code.append("                .condition(")
                    .append(expressions.conditionLambda(errorHandler.condition(), imports))
                    .append(")\n");
        }

        if (errorHandler.action() != null) {
            code.append("                .action(() -> { ")
                    .append(expressions.actionStatement(errorHandler.action(), imports))
                    .append(" })\n");
        }

        code.append("                .build());\n");
    }

    private void appendIteratorMethod(StringBuilder code,
                                      IteratorOperation operation,
                                      XmlGenerationContext context) {
        appendOperationMethod(code, operation.operation(), context);

        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        OperationSignature signature = context.signatures().get(operation);
        OperationSignature childSignature = context.signatures().get(operation.operation());
        String iteratorStation = imports.use("io.github.gear4jtest.core.api.station.IteratorStation");
        imports.addStatic(ELEMENT_MODEL_BUILDERS + ".chain");

        code.append(INDENT_PRIVATE).append(iteratorStation).append("<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append(METHOD_OPEN);
        String elementModelBuilders = imports.use(ELEMENT_MODEL_BUILDERS);
        code.append("        return ").append(elementModelBuilders).append(".<")
                .append(signature.inputType().render(imports)).append(">iterate(\"")
                .append(JavaStringEscaper.escapeJava(operation.id()))
                .append("\")\n");
        code.append("                .iterableFunction(")
                .append(expressions.normalizeExpression(operation.iterableFunction(), imports)).append(")\n");
        code.append("                .sequence(chain(\"").append(JavaStringEscaper.escapeJava(operation.id()))
                .append(":chain\", ")
                .append(XmlGeneratedNames.operationMethodName(operation.operation())).append("()).build())\n");

        if (operation.accumulator() != null) {
            String accumulator = switch (operation.accumulator().toUpperCase(Locale.ROOT)) {
                case "LIST" -> imports.use(ELEMENT_MODEL_BUILDERS) + ".toList()";
                case "SET" -> imports.use(ELEMENT_MODEL_BUILDERS) + ".toSet()";
                default -> throw new IllegalArgumentException("Unsupported accumulator: " + operation.accumulator());
            };
            code.append("                .accumulator(").append(accumulator).append(")\n");
        } else if (operation.collector() != null) {
            code.append("                .collector(")
                    .append(expressions.normalizeExpression(operation.collector(), imports))
                    .append(")\n");
        } else if (!signature.outputType().equals(childSignature.outputType())) {
            throw new IllegalArgumentException("Iterator '" + operation.id()
                    + "' has no collector/accumulator but output type differs from child output type");
        }

        code.append("                .build();\n");
        code.append(METHOD_END);
    }

    private void appendContainerMethod(StringBuilder code,
                                       ContainerOperation operation,
                                       XmlGenerationContext context) {
        for (SubLine subLine : operation.subLines()) {
            appendOperationMethod(code, subLine.operation(), context);
        }

        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        OperationSignature signature = context.signatures().get(operation);
        String containerBaseStation = imports.use("io.github.gear4jtest.core.api.station.ContainerBaseStation");
        imports.addStatic(ELEMENT_MODEL_BUILDERS + ".container");

        code.append(INDENT_PRIVATE).append(containerBaseStation).append("<")
                .append(signature.inputType().render(imports)).append(", ")
                .append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append(METHOD_OPEN);

        if (operation.parallel()) {
            String executorField = context.parallelExecutorFields().get(operation);
            if (executorField == null) {
                throw new IllegalArgumentException("Parallel container '" + operation.id()
                        + "' has no generated executor dependency");
            }
            code.append("        return container(").append(signature.inputType().renderClassLiteral(imports))
                    .append(", requireExecutorService(").append(executorField).append(", \"")
                    .append(JavaStringEscaper.escapeJava(XmlGeneratedNames.parallelExecutorBeanName(operation)))
                    .append("\"))\n");
        } else {
            code.append("        return container(").append(signature.inputType().renderClassLiteral(imports))
                    .append(")\n");
        }

        for (SubLine subLine : operation.subLines()) {
            code.append("                .withSubLine(\"").append(JavaStringEscaper.escapeJava(subLine.id()))
                    .append("\", ")
                    .append(XmlGeneratedNames.operationMethodName(subLine.operation())).append("()");
            if (subLine.condition() != null) {
                code.append(", ").append(expressions.conditionLambda(subLine.condition(), imports));
            }
            code.append(")\n");
        }

        if (operation.returnsFunction() != null) {
            code.append("                .returns(")
                    .append(expressions.normalizeExpression(operation.returnsFunction(), imports))
                    .append(");\n");
        } else {
            code.append("                .build();\n");
        }

        code.append(METHOD_END);
    }

    private void appendIfElseMethod(StringBuilder code,
                                    IfElseOperation operation,
                                    XmlGenerationContext context) {
        for (XmlAssemblyLineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            appendOperationMethod(code, conditionalOperation.operation(), context);
        }
        if (operation.elseOperation() != null) {
            appendOperationMethod(code, operation.elseOperation(), context);
        }

        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        OperationSignature signature = context.signatures().get(operation);
        String unaryIfElse = imports.use("io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation");
        imports.addStatic(ELEMENT_MODEL_BUILDERS + ".ifElseContainer");

        code.append(INDENT_PRIVATE).append(unaryIfElse).append("<").append(signature.inputType().render(imports))
                .append("> ").append(XmlGeneratedNames.operationMethodName(operation)).append(METHOD_OPEN);
        code.append("        return ifElseContainer(").append(signature.inputType().renderClassLiteral(imports))
                .append(")\n");

        for (XmlAssemblyLineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            code.append("                .conditionally(\"")
                    .append(JavaStringEscaper.escapeJava(conditionalOperation.id()))
                    .append("\", ")
                    .append(XmlGeneratedNames.operationMethodName(conditionalOperation.operation())).append("(), ")
                    .append(expressions.conditionLambda(conditionalOperation.condition(), imports)).append(")\n");
        }

        if (operation.elseOperation() == null) {
            throw new IllegalArgumentException(
                    "ifElseContainer requires an elseOperation because the current core builder has no build() method");
        }

        code.append("                .elseOp(\"").append(JavaStringEscaper.escapeJava(operation.elseOperation().id()))
                .append("\", ")
                .append(XmlGeneratedNames.operationMethodName(operation.elseOperation())).append("());\n");
        code.append(METHOD_END);
    }

    private void appendSignalMethod(StringBuilder code,
                                    SignalOperation operation,
                                    XmlGenerationContext context) {
        JavaImportManager imports = context.imports();
        XmlExpressionRenderer expressions = context.expressions();
        OperationSignature signature = context.signatures().get(operation);
        String signalStation = imports.use("io.github.gear4jtest.core.api.station.SignalStation");
        code.append(INDENT_PRIVATE).append(signalStation).append("<").append(signature.inputType().render(imports))
                .append("> ").append(XmlGeneratedNames.operationMethodName(operation)).append(METHOD_OPEN);
        code.append("        ").append(signalStation).append(BUILDER_TYPE_PREFIX)
                .append(signature.inputType().render(imports))
                .append("> builder = new ").append(signalStation).append(BUILDER_TYPE_PREFIX)
                .append(signature.inputType().render(imports)).append(">()\n");
        code.append("                .id(\"").append(JavaStringEscaper.escapeJava(operation.id())).append("\")\n");
        String stationSignalType = imports.use("io.github.gear4jtest.core.api.station.StationSignalType");
        code.append("                .type(").append(stationSignalType)
                .append(".").append(operation.type().toUpperCase(Locale.ROOT)).append(");\n");
        code.append("        builder.condition(")
                .append(expressions.signalConditionLambda(operation.condition(), signature.inputType(), imports))
                .append(");\n");
        code.append("        return builder.build();\n");
        code.append(METHOD_END);
    }
}
