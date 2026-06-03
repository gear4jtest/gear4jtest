package io.test.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    private final ClassLoader classLoader;
    private final JavaSourceFormatter formatter;
    private final XmlJavaSourcePolicy sourcePolicy;

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
    }

    private String valueExpression(ValueParameter parameter) {
        if ("java.lang.String".equals(parameter.valueType()) || "String".equals(parameter.valueType())) {
            return "\"" + escapeJava(parameter.value()) + "\"";
        }
        sourcePolicy.validateJavaExpression(parameter.value());
        return parameter.value();
    }

    private String conditionLambda(Condition condition, JavaImportManager imports) {
        String expression = normalizeExpression(condition.expression().trim(), imports);
        if (expression.contains("->")) {
            return expression;
        }
        return "(input, ctx) -> " + expression;
    }

    private String signalConditionLambda(Condition condition,
                                         JavaTypeName inputType,
                                         JavaImportManager imports) {
        if (condition == null) {
            return "sig -> true";
        }

        String expression = normalizeExpression(condition.expression().trim(), imports);
        if (expression.contains("->")) {
            return expression;
        }

        String type = inputType.render(imports);
        return "sig -> { " + type + " input = sig.getItem(); " + "var ctx = sig.getItemExecution(); " + "return "
                + expression + "; " + "}";
    }

    private String actionStatement(Action action, JavaImportManager imports) {
        String statement = normalizeExpression(action.expression().trim(), imports);
        return statement.endsWith(";") ? statement : statement + ";";
    }

    private String normalizeRetriever(String retriever, JavaTypeName operationType, JavaImportManager imports) {
        if (retriever == null) {
            return null;
        }
        sourcePolicy.validateJavaExpression(retriever);
        return retriever.replace(operationType.canonical() + "::", operationType.render(imports) + "::");
    }

    private String normalizeExpression(String expression, JavaImportManager imports) {
        if (expression == null) {
            return null;
        }
        sourcePolicy.validateJavaExpression(expression);
        String normalized = expression.trim();
        if (normalized.contains("java.util.function.Function.identity()")) {
            imports.addStatic("java.util.function.Function.identity");
            normalized = normalized.replace("java.util.function.Function.identity()", "identity()");
        }
        if (normalized.contains("java.util.stream.Collectors.toList()")) {
            imports.addStatic("java.util.stream.Collectors.toList");
            normalized = normalized.replace("java.util.stream.Collectors.toList()", "toList()");
        }
        if (normalized.contains("java.util.stream.Collectors.toSet()")) {
            imports.addStatic("java.util.stream.Collectors.toSet");
            normalized = normalized.replace("java.util.stream.Collectors.toSet()", "toSet()");
        }
        normalized = replaceTypeReference(normalized, imports, "java.util.HashMap");
        normalized = replaceTypeReference(normalized, imports, "java.util.LinkedHashMap");
        normalized = replaceTypeReference(normalized, imports, "java.util.ArrayList");
        normalized = replaceTypeReference(normalized, imports, "java.util.HashSet");
        normalized = replaceTypeReference(normalized, imports, "java.util.Arrays");
        return normalized;
    }

    private static String replaceTypeReference(String expression,
                                               JavaImportManager imports,
                                               String fullyQualifiedType) {
        if (!expression.contains(fullyQualifiedType)) {
            return expression;
        }
        return expression.replace(fullyQualifiedType, imports.use(fullyQualifiedType));
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    public OperationChainTranslator.GenerationResult generate(XmlPipelineDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        String simpleClassName = toTypeName(definition.id()) + "Line";
        String fqcn = packageName + "." + simpleClassName;

        JavaImportManager imports = new JavaImportManager(packageName);
        addStaticImports(imports);
        Map<Operation, OperationSignature> signatures = new OperationTypeResolver(classLoader).resolve(definition);

        StringBuilder body = new StringBuilder();
        body.append("/** Generated from a Gear4J XML pipeline definition. */\n");
        body.append("public final class ").append(simpleClassName).append(" implements ")
                .append(imports.use("io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine")).append(" {\n\n");

        Map<ContainerOperation, String> parallelExecutorFields = collectParallelExecutorFields(definition);

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
            code.append("    @").append(imports.use("io.test.gear4jtest.external.api.loader.Inject")).append("(\"")
                    .append(escapeJava(dependency.name())).append("\")\n");
            code.append("    private ").append(JavaTypeName.parse(dependency.type()).render(imports)).append(" ")
                    .append(toFieldName(dependency.name())).append(";\n\n");
        }
    }

    private void appendParallelExecutorDependencies(StringBuilder code,
                                                    JavaImportManager imports,
                                                    Map<ContainerOperation, String> executorFields) {
        if (executorFields.isEmpty()) {
            return;
        }

        String inject = imports.use("io.test.gear4jtest.external.api.loader.Inject");
        String executorService = imports.use("java.util.concurrent.ExecutorService");
        for (Map.Entry<ContainerOperation, String> entry : executorFields.entrySet()) {
            code.append("    @").append(inject).append("(\"")
                    .append(escapeJava(parallelExecutorBeanName(entry.getKey()))).append("\")\n");
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

    private Map<ContainerOperation, String> collectParallelExecutorFields(XmlPipelineDefinition definition) {
        Map<ContainerOperation, String> executorFields = new LinkedHashMap<>();
        Map<String, ContainerOperation> ownersByField = new LinkedHashMap<>();

        for (Operation operation : definition.operations()) {
            collectParallelExecutorField(operation, executorFields, ownersByField);
        }

        return new LinkedHashMap<>(executorFields);
    }

    private void collectParallelExecutorField(Operation operation,
                                              Map<ContainerOperation, String> executorFields,
                                              Map<String, ContainerOperation> ownersByField) {
        if (operation instanceof ContainerOperation containerOperation) {
            if (containerOperation.parallel()) {
                String fieldName = parallelExecutorFieldName(containerOperation);
                ContainerOperation previousOwner = ownersByField.putIfAbsent(fieldName, containerOperation);
                if (previousOwner != null && !previousOwner.equals(containerOperation)) {
                    throw new IllegalArgumentException("Parallel XML containers '" + previousOwner.id() + "' and '"
                            + containerOperation.id() + "' generate the same executor field '" + fieldName
                            + "'. Use ids that remain unique after Java identifier normalization.");
                }
                executorFields.putIfAbsent(containerOperation, fieldName);
            }
            for (SubLine subLine : containerOperation.subLines()) {
                collectParallelExecutorField(subLine.operation(), executorFields, ownersByField);
            }
            return;
        }

        if (operation instanceof IteratorOperation iteratorOperation) {
            collectParallelExecutorField(iteratorOperation.operation(), executorFields, ownersByField);
            return;
        }

        if (operation instanceof IfElseOperation ifElseOperation) {
            for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : ifElseOperation
                    .conditionalOperations()) {
                collectParallelExecutorField(conditionalOperation.operation(), executorFields, ownersByField);
            }
            if (ifElseOperation.elseOperation() != null) {
                collectParallelExecutorField(ifElseOperation.elseOperation(), executorFields, ownersByField);
            }
        }
    }

    private static String parallelExecutorBeanName(ContainerOperation operation) {
        return "gear4j.executor." + operation.id();
    }

    private static String parallelExecutorFieldName(ContainerOperation operation) {
        return "gear4j" + toTypeName(operation.id()) + "ExecutorService";
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
                .append(">createAssemblyLine(\"").append(escapeJava(definition.id())).append("\")\n");

        for (Operation operation : definition.operations()) {
            code.append("                .then(").append(methodName(operation)).append("())\n");
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
        String methodName = methodName(operation);
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
                .append(", ").append(signature.outputType().render(imports)).append("> ").append(methodName(operation))
                .append("() {\n");
        code.append("        ").append(workStation).append(".Builder<").append(signature.inputType().render(imports))
                .append(", ").append(signature.outputType().render(imports)).append(", ")
                .append(operationType.render(imports)).append("> builder = processingOperation(\"")
                .append(escapeJava(operation.id())).append("\", ").append(operationType.renderClassLiteral(imports))
                .append(");\n");

        for (Parameter parameter : operation.parameters().parameters()) {
            if (parameter instanceof ValueParameter valueParameter) {
                code.append("        builder.parameter(")
                        .append(normalizeRetriever(valueParameter.retriever(), operationType, imports)).append(", ")
                        .append(valueExpression(valueParameter)).append(");\n");
            } else if (parameter instanceof SupplierParameter supplierParameter) {
                code.append("        builder.parameter(")
                        .append(normalizeRetriever(supplierParameter.retriever(), operationType, imports)).append(", ")
                        .append(normalizeExpression(supplierParameter.supplier(), imports)).append(");\n");
            } else if (parameter instanceof ContextParameter contextParameter) {
                code.append("        builder.parameter(")
                        .append(normalizeRetriever(contextParameter.retriever(), operationType, imports)).append(", ")
                        .append(normalizeExpression(contextParameter.function(), imports)).append(");\n");
            }
        }

        for (ErrorHandler errorHandler : operation.errorHandlers()) {
            appendErrorHandler(code, imports, errorHandler, signature.inputType());
        }

        for (Condition condition : operation.conditions()) {
            code.append("        builder.skipIf(").append(conditionLambda(condition, imports)).append(");\n");
        }

        if (operation.fallbackTransformer() != null) {
            code.append("        builder.fallback((input, ctx) -> ")
                    .append(normalizeExpression(operation.fallbackTransformer().expression(), imports)).append(");\n");
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
            code.append("                .condition(").append(conditionLambda(errorHandler.condition(), imports))
                    .append(")\n");
        }

        if (errorHandler.action() != null) {
            code.append("                .action(() -> { ").append(actionStatement(errorHandler.action(), imports))
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
                .append(", ").append(signature.outputType().render(imports)).append("> ").append(methodName(operation))
                .append("() {\n");
        String elementModelBuilders = imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders");
        code.append("        return ").append(elementModelBuilders).append(".<")
                .append(signature.inputType().render(imports)).append(">iterate(\"").append(escapeJava(operation.id()))
                .append("\")\n");
        code.append("                .iterableFunction(")
                .append(normalizeExpression(operation.iterableFunction(), imports)).append(")\n");
        code.append("                .pipeline(chain(\"").append(escapeJava(operation.id())).append(":chain\", ")
                .append(methodName(operation.operation())).append("()).build())\n");

        if (operation.accumulator() != null) {
            String accumulator = switch (operation.accumulator().toUpperCase(Locale.ROOT)) {
                case "LIST" -> imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders") + ".toList()";
                case "SET" -> imports.use("io.github.gear4jtest.core.api.util.ElementModelBuilders") + ".toSet()";
                default -> throw new IllegalArgumentException("Unsupported accumulator: " + operation.accumulator());
            };
            code.append("                .accumulator(").append(accumulator).append(")\n");
        } else if (operation.collector() != null) {
            code.append("                .collector(").append(normalizeExpression(operation.collector(), imports))
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
                .append(signature.outputType().render(imports)).append("> ").append(methodName(operation))
                .append("() {\n");

        if (operation.parallel()) {
            String executorField = parallelExecutorFields.get(operation);
            if (executorField == null) {
                throw new IllegalArgumentException("Parallel container '" + operation.id()
                        + "' has no generated executor dependency");
            }
            code.append("        return container(").append(signature.inputType().renderClassLiteral(imports))
                    .append(", requireExecutorService(").append(executorField).append(", \"")
                    .append(escapeJava(parallelExecutorBeanName(operation))).append("\"))\n");
        } else {
            code.append("        return container(").append(signature.inputType().renderClassLiteral(imports))
                    .append(")\n");
        }

        for (SubLine subLine : operation.subLines()) {
            code.append("                .withSubLine(\"").append(escapeJava(subLine.id())).append("\", ")
                    .append(methodName(subLine.operation())).append("()");
            if (subLine.condition() != null) {
                code.append(", ").append(conditionLambda(subLine.condition(), imports));
            }
            code.append(")\n");
        }

        if (operation.returnsFunction() != null) {
            code.append("                .returns(").append(normalizeExpression(operation.returnsFunction(), imports))
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
                .append("> ").append(methodName(operation)).append("() {\n");
        code.append("        return ifElseContainer(").append(signature.inputType().renderClassLiteral(imports))
                .append(")\n");

        for (XmlPipelineDefinition.ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            code.append("                .conditionally(\"").append(escapeJava(conditionalOperation.id()))
                    .append("\", ")
                    .append(methodName(conditionalOperation.operation())).append("(), ")
                    .append(conditionLambda(conditionalOperation.condition(), imports)).append(")\n");
        }

        if (operation.elseOperation() == null) {
            throw new IllegalArgumentException(
                    "ifElseContainer requires an elseOperation because the current core builder has no build() method");
        }

        code.append("                .elseOp(\"").append(escapeJava(operation.elseOperation().id())).append("\", ")
                .append(methodName(operation.elseOperation())).append("());\n");
        code.append("    }\n\n");
    }

    private void appendSignalMethod(StringBuilder code,
                                    JavaImportManager imports,
                                    SignalOperation operation,
                                    OperationSignature signature) {
        String signalStation = imports.use("io.github.gear4jtest.core.api.station.SignalStation");
        code.append("    private ").append(signalStation).append("<").append(signature.inputType().render(imports))
                .append("> ").append(methodName(operation)).append("() {\n");
        code.append("        ").append(signalStation).append(".Builder<").append(signature.inputType().render(imports))
                .append("> builder = new ").append(signalStation).append(".Builder<")
                .append(signature.inputType().render(imports)).append(">()\n");
        code.append("                .id(\"").append(escapeJava(operation.id())).append("\")\n");
        code.append("                .type(").append(imports.use("io.github.gear4jtest.core.api.behavior.SignalType"))
                .append(".").append(operation.type().toUpperCase(Locale.ROOT)).append(");\n");
        code.append("        builder.condition(")
                .append(signalConditionLambda(operation.condition(), signature.inputType(), imports)).append(");\n");
        code.append("        return builder.build();\n");
        code.append("    }\n\n");
    }
}
