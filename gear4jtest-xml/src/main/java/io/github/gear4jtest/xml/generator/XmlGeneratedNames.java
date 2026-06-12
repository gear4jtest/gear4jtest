package io.github.gear4jtest.xml.generator;

import java.util.Locale;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;

/**
 * Centralizes Java names derived from XML identifiers.
 *
 * <p>
 * Keeping this logic outside {@link XmlToJavaGenerator} avoids scattering
 * normalization rules across the code generator and makes future naming
 * compatibility changes easier to test in isolation.
 * </p>
 */
final class XmlGeneratedNames {
    private XmlGeneratedNames() {
    }

    static String operationMethodName(Operation operation) {
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

    static String toTypeName(String value) {
        String identifier = toJavaIdentifier(value);
        return identifier.substring(0, 1).toUpperCase(Locale.ROOT) + identifier.substring(1);
    }

    static String toFieldName(String value) {
        String identifier = toJavaIdentifier(value);
        return Character.toLowerCase(identifier.charAt(0)) + identifier.substring(1);
    }

    static String parallelExecutorBeanName(ContainerOperation operation) {
        return "gear4j.executor." + operation.id();
    }

    static String parallelExecutorFieldName(ContainerOperation operation) {
        return "gear4j" + toTypeName(operation.id()) + "ExecutorService";
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
}
