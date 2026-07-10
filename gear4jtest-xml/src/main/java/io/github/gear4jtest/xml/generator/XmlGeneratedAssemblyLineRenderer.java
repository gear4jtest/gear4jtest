package io.github.gear4jtest.xml.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;

final class XmlGeneratedAssemblyLineRenderer {
    private final String packageName;
    private final ClassLoader classLoader;
    private final XmlExpressionRenderer expressions;
    private final XmlGeneratedClassSupportRenderer supportRenderer = new XmlGeneratedClassSupportRenderer();
    private final XmlOperationMethodRenderer operationRenderer = new XmlOperationMethodRenderer();

    XmlGeneratedAssemblyLineRenderer(String packageName, ClassLoader classLoader, XmlJavaSourcePolicy sourcePolicy) {
        this.packageName = Objects.requireNonNull(packageName, "packageName must not be null");
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
        this.expressions = new XmlExpressionRenderer(XmlJavaSourcePolicy.require(sourcePolicy));
    }

    GeneratedJavaSource render(XmlAssemblyLineDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        String simpleClassName = XmlGeneratedNames.toTypeName(definition.id()) + "Line";
        String fullyQualifiedClassName = packageName + "." + simpleClassName;

        JavaImportManager imports = new JavaImportManager(packageName);
        Map<Operation, OperationSignature> signatures = new OperationTypeResolver(classLoader).resolve(definition);
        JavaTypeName inputType = JavaTypeName.parse(definition.inputType());
        JavaTypeName outputType = inputType;
        for (Operation operation : definition.operations()) {
            outputType = signatures.get(operation).outputType();
        }
        Map<ContainerOperation, String> parallelExecutorFields = XmlParallelExecutorDependencies.collect(definition);
        XmlGenerationContext context = new XmlGenerationContext(
                imports, expressions, signatures, parallelExecutorFields, new LinkedHashMap<>());

        StringBuilder body = new StringBuilder();
        body.append("/** Generated from a Gear4J XML pipeline definition. */\n");
        body.append("public final class ").append(simpleClassName).append(" implements ")
                .append(imports.use("io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine"))
                .append("<").append(inputType.render(imports)).append(", ").append(outputType.render(imports))
                .append("> {\n\n");

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
