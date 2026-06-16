package io.github.gear4jtest.xml.generator;

import java.util.Map;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;

final class XmlGeneratedClassSupportRenderer {
    void appendDependencies(StringBuilder code, JavaImportManager imports, XmlPipelineDefinition definition) {
        for (XmlPipelineDefinition.Dependency dependency : definition.dependencies()) {
            code.append("    @").append(imports.use("io.github.gear4jtest.external.api.loader.Inject")).append("(\"")
                    .append(JavaStringEscaper.escapeJava(dependency.name())).append("\")\n");
            code.append("    private ").append(JavaTypeName.parse(dependency.type()).render(imports)).append(" ")
                    .append(XmlGeneratedNames.toFieldName(dependency.name())).append(";\n\n");
        }
    }

    void appendParallelExecutorDependencies(StringBuilder code,
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

    void appendConstructor(StringBuilder code, String simpleClassName) {
        code.append("    public ").append(simpleClassName).append("() {\n");
        code.append("    }\n\n");
    }

    void appendGelHelper(StringBuilder code, JavaImportManager imports, XmlPipelineDefinition definition) {
        if (!XmlGelUsageAnalyzer.usesGel(definition)) {
            return;
        }
        String map = imports.use("java.util.Map");
        String concurrentHashMap = imports.use("java.util.concurrent.ConcurrentHashMap");
        String gearExpression = imports.use("io.github.gear4jtest.xml.expression.GearExpression");
        String gearExpressionContext = imports.use("io.github.gear4jtest.xml.expression.GearExpressionContext");
        String gearExpressionParser = imports.use("io.github.gear4jtest.xml.expression.GearExpressionParser");
        String executionContext = imports.use("io.github.gear4jtest.core.api.context.ExecutionContext");
        code.append("    private static final ").append(map).append("<String, ").append(gearExpression)
                .append("> GEL_EXPRESSIONS = new ").append(concurrentHashMap).append("<>();\n\n");
        code.append("    private static boolean evaluateGel(String expression, Object input, ")
                .append(executionContext).append(" ctx) {\n");
        code.append("        return GEL_EXPRESSIONS.computeIfAbsent(expression, ")
                .append(gearExpressionParser).append("::parse)\n");
        code.append("                .evaluateBoolean(new ").append(gearExpressionContext)
                .append("(input, ctx == null ? null : ctx.getContext()));\n");
        code.append("    }\n\n");
    }

    void appendRequireExecutorServiceMethod(StringBuilder code,
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
        code.append("            throw new IllegalStateException(\"Missing required ExecutorService bean '\"")
                .append(" + beanName + ")
                .append("\"' for parallel XML container\");\n");
        code.append("        }\n");
        code.append("        return executorService;\n");
        code.append("    }\n\n");
    }

    void appendConfigurationMethod(StringBuilder code, JavaImportManager imports, XmlPipelineDefinition definition) {
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

    void appendAssemblyMethod(StringBuilder code,
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
}
