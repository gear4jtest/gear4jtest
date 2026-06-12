package io.github.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContextParameter;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ErrorHandler;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Parameter;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SupplierParameter;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ValueParameter;

public final class XmlToJavaGenerator {
    public static final String DEFAULT_PACKAGE = "io.github.gear4jtest.xml.generated";
    private final String packageName;
    private final ClassLoader classLoader;
    private final JavaSourceFormatter formatter;
    private final XmlJavaSourcePolicy sourcePolicy;
    private final XmlExpressionRenderer expressions;

    public XmlToJavaGenerator() {
        this(DEFAULT_PACKAGE);
    }

    public XmlToJavaGenerator(String packageName) {
        this(packageName, contextClassLoader());
    }

    public XmlToJavaGenerator(String packageName, ClassLoader classLoader) {
        this(packageName, classLoader, JdtFormatter.defaultFormatter());
    }

    public XmlToJavaGenerator(String packageName, ClassLoader classLoader, JavaSourceFormatter formatter) {
        this(packageName, classLoader, formatter, XmlJavaSourcePolicy.trusted());
    }

    public XmlToJavaGenerator(String packageName,
                              ClassLoader classLoader,
                              JavaSourceFormatter formatter,
                              XmlJavaSourcePolicy sourcePolicy) {
        this.sourcePolicy = XmlJavaSourcePolicy.require(sourcePolicy);
        this.sourcePolicy.validatePackageName(packageName);
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.classLoader = classLoader != null ? classLoader : contextClassLoader();
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
        this.expressions = new XmlExpressionRenderer(this.sourcePolicy);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    public OperationChainTranslator.GenerationResult generate(XmlPipelineDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        String simpleClassName = XmlGeneratedNames.toTypeName(definition.id()) + "Line";
        String fqcn = packageName + "." + simpleClassName;

        JavaImportManager imports = new JavaImportManager(packageName);
        addStaticImports(imports);
        Map<Operation, OperationSignature> signatures = new OperationTypeResolver(classLoader).resolve(definition);

        StringBuilder body = new StringBuilder();
        body.append("/** Generated from a Gear4J XML pipeline definition. */\n");
        body.append("public final class ").append(simpleClassName).append(" implements ")
                .append(imports.use("io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine")).append(" {\n\n");

        Map<ContainerOperation, String> parallelExecutorFields = XmlParallelExecutorDependencies.collect(definition);

        appendDependencies(body, imports, definition);
        appendParallelExecutorDependencies(body, imports, parallelExecutorFields);
        appendConstructor(body, simpleClassName);
        appendRequireExecutorServiceMethod(body, imports, parallelExecutorFields);

        Map<String, Operation> emittedMethods = new LinkedHashMap<>();
        for (Operation operation : definition.operations()) {
            appendOperationMethod(body, imports, operation, emittedMethods, parallelExecutorFields, signatures);
        }

        appendConfigurationMethod(body, imports, definition);
        appendAssemblyMethod(body, imports, definition, signatures);
        body.append("}\n");

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append(imports.renderImports());
        code.append(body);

        return new OperationChainTranslator.GenerationResult(fqcn, formatter.format(code.toString()));
    }

    private void addStaticImports(JavaImportManager imports) {
        // Static imports are registered lazily by the snippets that actually use them.
    }

    private void appendDependencies(StringBuilder code, JavaImportManager imports, XmlPipelineDefinition definition) {
        for (XmlPipelineDefinition.Dependency dependency : definition.dependencies()) {
            code.append("    @").append(imports.use("io.github.gear4jtest.external.api.loader.Inject")).append("(\"")
                    .append(JavaStringEscaper.escapeJava(dependency.name())).append("\")\n");
            code.append("    private ").append(JavaTypeName.parse(dependency.type()).render(imports)).append(" ")
                    .append(XmlGeneratedNames.toFieldName(dependency.name())).append(";\n\n");
        }
    }

    private void appendParallelExecutorDependencies(StringBuilder code,
                                                    JavaImportManager imports,
                                                    Map<ContainerOperation, String> executorFields) {
        if (executorFields.isEmpty()) {
            return;
        }

        String inject = imports.use("io.github.gear4jtest.external.api.loader.Inject");
        String executorService = imports.use("java.util.concurrent.ExecutorService");
        for (Map.Entry<ContainerOperation, String> entry : executorFields.entrySet()) {
            code.append("    @").append(inject).append("(\"")
                    .append(JavaStringEscaper.escapeJava(XmlGeneratedNames.parallelExecutorBeanName(entry.getKey())))
                    .append("\")\n");
            code.append("    private ").append(executorService).append(" ").append(entry.getValue())
                    .append(";\n\n");
        }
    }

    private void appendRequireExecutorServiceMethod(StringBuilder code,
                                                    JavaImportManager imports,
                                                    Map<ContainerOperation, String> executorFields) {
        if (executorFields.isEmpty()) {
            return;
        }

        String executorService = imports.use("java.util.concurrent.ExecutorService");
        code.append("    private static ").append(executorService)
                .append(" requireExecutorService(").append(executorService)
                .append(" executorService, String beanName) {\n");
        code.append("        if (executorService == null) {\n");
        code.append("            throw new IllegalStateException(\"Missing required ExecutorService bean '\" + beanName + \"' for parallel XML container\");\n");
        code.append("        }\n");
        code.append("        return executorService;\n");
        code.append("    }\n\n");
    }

    private void appendConstructor(StringBuilder code, String simpleClassName) {
        code.append("    public ").append(simpleClassName).append("() {\n");
        code.append("    }\n\n");
    }

    private void appendAssemblyMethod(StringBuilder code,
                                      JavaImportManager imports,
                                      XmlPipelineDefinition definition,
                                      Map<Operation, OperationSignature> signatures) {
        JavaTypeName inputType = JavaTypeName.parse(definition.inputType());
        JavaTypeName outputType = resolvePipelineOutput(definition, signatures, inputType);

        code.append("    @Override\n");
        code.append("    public ").append(imports.use("io.github.gear4jtest.core.api.AssemblyLine")).append("<")
                .append(inputType.render(imports)).append(", ").append(outputType.render(imports))
                .append("> getAssemblyLineDefinition() {\n");
        String elementModelBuilders = imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders");
        code.append("        return ").append(elementModelBuilders).append(".<").append(inputType.render(imports))
                .append(">createAssemblyLine(\"").append(JavaStringEscaper.escapeJava(definition.id())).append("\")\n");

        for (Operation operation : definition.operations()) {
            code.append("                .then(").append(XmlGeneratedNames.operationMethodName(operation))
                    .append("())\n");
        }

        if (definition.configuration() != null) {
            code.append("                .configuration(createConfiguration())\n");
        }

        code.append("                .build();\n");
        code.append("    }\n\n");
    }

    private JavaTypeName resolvePipelineOutput(XmlPipelineDefinition definition,
                                               Map<Operation, OperationSignature> signatures,
                                               JavaTypeName inputType) {
        JavaTypeName current = inputType;
        for (Operation operation : definition.operations()) {
            current = signatures.get(operation).outputType();
        }
        return current;
    }

    private void appendConfigurationMethod(StringBuilder code,
                                           JavaImportManager imports,
                                           XmlPipelineDefinition definition) {
        if (definition.configuration() == null) {
            return;
        }

        String configuration = imports.use("io.github.gear4jtest.core.api.AssemblyLine") + ".Configuration";
        code.append("    private ").append(configuration).append(" createConfiguration() {\n");
        code.append("        ").append(configuration).append(".Builder builder = ").append(configuration)
                .append(".builder();\n");

        if (definition.configuration().eventHandling() != null) {
            boolean eventOnParameterChanged = Boolean.TRUE
                    .equals(definition.configuration().eventHandling().eventOnParameterChanged());
            String eventHandlingDefinition = imports
                    .use("io.github.gear4jtest.core.api.config.EventHandlingDefinition");
            code.append("        builder.eventHandling(").append(eventHandlingDefinition).append(".builder()\n");
            code.append("                .globalEventConfiguration(").append(eventHandlingDefinition)
                    .append(".EventConfiguration.builder()\n");
            code.append("                        .eventOnParameterChanged(").append(eventOnParameterChanged)
                    .append(")\n");
            code.append("                        .build())\n");
            code.append("                .build());\n");
        }

        if (definition.configuration().persistence() != null) {
            boolean storeResultObject = !Boolean.FALSE
                    .equals(definition.configuration().persistence().storeResultObject());
            code.append("        builder.persistence(")
                    .append(imports.use("io.github.gear4jtest.core.api.config.PersistenceConfiguration"))
                    .append(".builder()\n");
            code.append("                .storeResultObject(").append(storeResultObject).append(")\n");
            code.append("                .build());\n");
        }

        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }

    private void appendOperationMethod(StringBuilder code,
                                       JavaImportManager imports,
                                       Operation operation,
                                       Map<String, Operation> emittedMethods,
                                       Map<ContainerOperation, String> parallelExecutorFields,
                                       Map<Operation, OperationSignature> signatures) {
        String methodName = XmlGeneratedNames.operationMethodName(operation);
        Operation previousOperation = emittedMethods.putIfAbsent(methodName, operation);
        if (previousOperation != null) {
            if (previousOperation.equals(operation)) {
                return;
            }
            throw new IllegalArgumentException("Generated method name collision for method '" + methodName
                    + "' between operations '" + previousOperation.id() + "' and '" + operation.id()
                    + "'. Use ids that remain unique after Java identifier normalization.");
        }

        if (operation instanceof ProcessingOperation processingOperation) {
            appendProcessingMethod(code, imports, processingOperation, signatures.get(processingOperation));
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            appendIteratorMethod(code, imports, iteratorOperation, emittedMethods, parallelExecutorFields, signatures);
        } else if (operation instanceof ContainerOperation containerOperation) {
            appendContainerMethod(code, imports, containerOperation, emittedMethods, parallelExecutorFields,
                                  signatures);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            appendIfElseMethod(code, imports, ifElseOperation, emittedMethods, parallelExecutorFields, signatures);
        } else if (operation instanceof SignalOperation signalOperation) {
            appendSignalMethod(code, imports, signalOperation, signatures.get(signalOperation));
        } else {
            throw new IllegalArgumentException("Unsupported operation type: " + operation);
        }
    }

    private void appendProcessingMethod(StringBuilder code,
                                        JavaImportManager imports,
                                        ProcessingOperation operation,
                                        OperationSignature signature) {
        JavaTypeName operationType = JavaTypeName.parse(operation.type());
        String workStation = imports.use("io.github.gear4jtest.core.api.station.WorkStation");
        imports.addStatic("io.github.gear4jtest.core.api.util.ElementModelBuilders.processingOperation");

        code.append("    private ").append(workStation).append("<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append("() {\n");
        code.append("        ").append(workStation).append(".Builder<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append(", ")
                .append(operationType.render(imports)).append("> builder = processingOperation(\"")
                .append(JavaStringEscaper.escapeJava(operation.id())).append("\", ")
                .append(operationType.renderClassLiteral(imports))
                .append(");\n");

        for (Parameter parameter : operation.parameters().parameters()) {
            if (parameter instanceof ValueParameter valueParameter) {
                code.append("        builder.parameter(")
                        .append(expressions.normalizeRetriever(valueParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.valueExpression(valueParameter)).append(");\n");
            } else if (parameter instanceof SupplierParameter supplierParameter) {
                code.append("        builder.parameter(")
                        .append(expressions.normalizeRetriever(supplierParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.normalizeExpression(supplierParameter.supplier(), imports)).append(");\n");
            } else if (parameter instanceof ContextParameter contextParameter) {
                code.append("        builder.parameter(")
                        .append(expressions.normalizeRetriever(contextParameter.retriever(), operationType, imports))
                        .append(", ")
                        .append(expressions.normalizeExpression(contextParameter.function(), imports)).append(");\n");
            }
        }

        for (ErrorHandler errorHandler : operation.errorHandlers()) {
            appendErrorHandler(code, imports, errorHandler, signature.inputType());
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
        code.append("    }\n\n");
    }

    private void appendErrorHandler(StringBuilder code,
                                    JavaImportManager imports,
                                    ErrorHandler errorHandler,
                                    JavaTypeName inputType) {
        String builder = switch (errorHandler.signalType().toUpperCase(Locale.ROOT)) {
            case "FATAL" -> "fatal";
            case "STOP" -> "stop";
            case "IGNORE" -> "ignore";
            default ->
                throw new IllegalArgumentException("Unsupported error signal type: " + errorHandler.signalType());
        };

        if (!"IGNORE".equals(errorHandler.signalType().toUpperCase(Locale.ROOT))) {
            imports.addStatic("io.github.gear4jtest.core.api.util.ElementModelBuilders." + builder);
        }

        String elementModelBuilders = imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders");
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
                                      JavaImportManager imports,
                                      IteratorOperation operation,
                                      Map<String, Operation> emittedMethods,
                                      Map<ContainerOperation, String> parallelExecutorFields,
                                      Map<Operation, OperationSignature> signatures) {
        appendOperationMethod(code, imports, operation.operation(), emittedMethods, parallelExecutorFields, signatures);

        OperationSignature signature = signatures.get(operation);
        OperationSignature childSignature = signatures.get(operation.operation());
        String iteratorStation = imports.use("io.github.gear4jtest.core.api.station.IteratorStation");
        imports.addStatic("io.github.gear4jtest.core.api.util.ElementModelBuilders.chain");

        code.append("    private ").append(iteratorStation).append("<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append("() {\n");
        String elementModelBuilders = imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders");
        code.append("        return ").append(elementModelBuilders).append(".<")
                .append(signature.inputType().render(imports)).append(">iterate(\"")
                .append(JavaStringEscaper.escapeJava(operation.id()))
                .append("\")\n");
        code.append("                .iterableFunction(")
                .append(expressions.normalizeExpression(operation.iterableFunction(), imports)).append(")\n");
        code.append("                .pipeline(chain(\"").append(JavaStringEscaper.escapeJava(operation.id()))
                .append(":chain\", ")
                .append(XmlGeneratedNames.operationMethodName(operation.operation())).append("()).build())\n");

        if (operation.accumulator() != null) {
            String accumulator = switch (operation.accumulator().toUpperCase(Locale.ROOT)) {
                case "LIST" -> imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders") + ".toList()";
                case "SET" -> imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders") + ".toSet()";
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
        code.append("    }\n\n");
    }

    private void appendContainerMethod(StringBuilder code,
                                       JavaImportManager imports,
                                       ContainerOperation operation,
                                       Map<String, Operation> emittedMethods,
                                       Map<ContainerOperation, String> parallelExecutorFields,
                                       Map<Operation, OperationSignature> signatures) {
        for (SubLine subLine : operation.subLines()) {
            appendOperationMethod(code, imports, subLine.operation(), emittedMethods, parallelExecutorFields,
                                  signatures);
        }

        OperationSignature signature = signatures.get(operation);
        String containerBaseStation = imports.use("io.github.gear4jtest.core.api.station.ContainerBaseStation");
        imports.addStatic("io.github.gear4jtest.core.api.util.ElementModelBuilders.container");

        code.append("    private ").append(containerBaseStation).append("<")
                .append(signature.inputType().render(imports)).append(", ")
                .append(signature.outputType().render(imports)).append("> ")
                .append(XmlGeneratedNames.operationMethodName(operation))
                .append("() {\n");

        if (operation.parallel()) {
            String executorField = parallelExecutorFields.get(operation);
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

        code.append("    }\n\n");
    }

    private void appendIfElseMethod(StringBuilder code,
                                    JavaImportManager imports,
                                    IfElseOperation operation,
                                    Map<String, Operation> emittedMethods,
                                    Map<ContainerOperation, String> parallelExecutorFields,
                                    Map<Operation, OperationSignature> signatures) {
        for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            appendOperationMethod(code, imports, conditionalOperation.operation(), emittedMethods,
                                  parallelExecutorFields,
                                  signatures);
        }
        if (operation.elseOperation() != null) {
            appendOperationMethod(code, imports, operation.elseOperation(), emittedMethods, parallelExecutorFields,
                                  signatures);
        }

        OperationSignature signature = signatures.get(operation);
        String unaryIfElse = imports.use("io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation");
        imports.addStatic("io.github.gear4jtest.core.api.util.ElementModelBuilders.ifElseContainer");

        code.append("    private ").append(unaryIfElse).append("<").append(signature.inputType().render(imports))
                .append("> ").append(XmlGeneratedNames.operationMethodName(operation)).append("() {\n");
        code.append("        return ifElseContainer(").append(signature.inputType().renderClassLiteral(imports))
                .append(")\n");

        for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
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
        code.append("    }\n\n");
    }

    private void appendSignalMethod(StringBuilder code,
                                    JavaImportManager imports,
                                    SignalOperation operation,
                                    OperationSignature signature) {
        String signalStation = imports.use("io.github.gear4jtest.core.api.station.SignalStation");
        code.append("    private ").append(signalStation).append("<").append(signature.inputType().render(imports))
                .append("> ").append(XmlGeneratedNames.operationMethodName(operation)).append("() {\n");
        code.append("        ").append(signalStation).append(".Builder<").append(signature.inputType().render(imports))
                .append("> builder = new ").append(signalStation).append(".Builder<")
                .append(signature.inputType().render(imports)).append(">()\n");
        code.append("                .id(\"").append(JavaStringEscaper.escapeJava(operation.id())).append("\")\n");
        code.append("                .type(").append(imports.use("io.github.gear4jtest.core.api.behavior.SignalType"))
                .append(".").append(operation.type().toUpperCase(Locale.ROOT)).append(");\n");
        code.append("        builder.condition(")
                .append(expressions.signalConditionLambda(operation.condition(), signature.inputType(), imports))
                .append(");\n");
        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }
}
