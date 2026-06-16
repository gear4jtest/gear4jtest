package io.github.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;

final class XmlGeneratedPipelineRenderer {
    private final String packageName;
    private final ClassLoader classLoader;
    private final XmlExpressionRenderer expressions;
    private final XmlGeneratedClassSupportRenderer supportRenderer = new XmlGeneratedClassSupportRenderer();
    private final XmlOperationMethodRenderer operationRenderer = new XmlOperationMethodRenderer();

    XmlGeneratedPipelineRenderer(String packageName, ClassLoader classLoader, XmlJavaSourcePolicy sourcePolicy) {
        this.packageName = Objects.requireNonNull(packageName, "packageName must not be null");
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
        this.expressions = new XmlExpressionRenderer(XmlJavaSourcePolicy.require(sourcePolicy));
    }

    GeneratedJavaSource render(XmlPipelineDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        String simpleClassName = XmlGeneratedNames.toTypeName(definition.id()) + "Line";
        String fullyQualifiedClassName = packageName + "." + simpleClassName;

        JavaImportManager imports = new JavaImportManager(packageName);
        Map<Operation, OperationSignature> signatures = new OperationTypeResolver(classLoader).resolve(definition);
        Map<ContainerOperation, String> parallelExecutorFields = XmlParallelExecutorDependencies.collect(definition);
        XmlGenerationContext context = new XmlGenerationContext(
                imports, expressions, signatures, parallelExecutorFields, new LinkedHashMap<>());

        StringBuilder body = new StringBuilder();
        body.append("/** Generated from a Gear4J XML pipeline definition. */\n");
        body.append("public final class ").append(simpleClassName).append(" implements ")
                .append(imports.use("io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine"))
                .append(" {\n\n");

        supportRenderer.appendDependencies(body, imports, definition);
        supportRenderer.appendParallelExecutorDependencies(body, imports, parallelExecutorFields);
        supportRenderer.appendConstructor(body, simpleClassName);
        supportRenderer.appendGelHelper(body, imports, definition);
        supportRenderer.appendRequireExecutorServiceMethod(body, imports, parallelExecutorFields);

        for (Operation operation : definition.operations()) {
            operationRenderer.appendOperationMethod(body, operation, context);
        }

        supportRenderer.appendConfigurationMethod(body, imports, definition);
        supportRenderer.appendAssemblyMethod(body, imports, definition, signatures);
        body.append("}\n");

        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append(imports.renderImports());
        source.append(body);

        return new GeneratedJavaSource(fullyQualifiedClassName, source.toString());
    }
}
