package io.github.gear4jtest.xml.limit;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.translator.XmlTranslationLimits;

/**
 * Per-definition counter shared by the parser and the standalone generator.
 */
public final class XmlDefinitionBudget {
    private final XmlTranslationLimits limits;
    private int operations;

    public XmlDefinitionBudget(XmlTranslationLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    public void recordOperation(int depth) {
        if (depth > limits.maxNestingDepth()) {
            throw new IllegalArgumentException("XML definition exceeds maxNestingDepth="
                    + limits.maxNestingDepth());
        }
        operations++;
        if (operations > limits.maxOperations()) {
            throw new IllegalArgumentException("XML definition exceeds maxOperations=" + limits.maxOperations());
        }
    }

    public void requireDependencies(int count) {
        if (count > limits.maxDependencies()) {
            throw new IllegalArgumentException("XML definition exceeds maxDependencies="
                    + limits.maxDependencies());
        }
    }

    public void requireGeneratedSource(String source, String stage) {
        Objects.requireNonNull(source, "source must not be null");
        long sourceBytes = utf8Length(source);
        if (sourceBytes > limits.maxGeneratedSourceBytes()) {
            throw new IllegalArgumentException(stage + " exceeds maxGeneratedSourceBytes="
                    + limits.maxGeneratedSourceBytes() + ": " + sourceBytes + " bytes");
        }
    }

    public static void validateDefinition(XmlAssemblyLineDefinition definition, XmlTranslationLimits limits) {
        Objects.requireNonNull(definition, "definition must not be null");
        XmlDefinitionBudget budget = new XmlDefinitionBudget(limits);
        List<XmlAssemblyLineDefinition.Dependency> dependencies = definition.dependencies();
        budget.requireDependencies(dependencies == null ? 0 : dependencies.size());
        List<Operation> operations = Objects.requireNonNull(definition.operations(), "operations must not be null");
        operations.forEach(operation -> budget.visit(operation, 1));
    }

    private void visit(Operation operation, int depth) {
        recordOperation(depth);
        if (operation instanceof IteratorOperation iterator) {
            visit(iterator.operation(), depth + 1);
        } else if (operation instanceof ContainerOperation container) {
            container.subLines().forEach(subLine -> visit(subLine.operation(), depth + 1));
        } else if (operation instanceof IfElseOperation ifElse) {
            ifElse.conditionalOperations()
                    .forEach(conditional -> visit(conditional.operation(), depth + 1));
            if (ifElse.elseOperation() != null) {
                visit(ifElse.elseOperation(), depth + 1);
            }
        }
    }

    private static long utf8Length(String value) {
        long bytes = 0L;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current <= 0x7f) {
                bytes++;
            } else if (current <= 0x7ff) {
                bytes += 2L;
            } else if (Character.isHighSurrogate(current)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4L;
                index++;
            } else if (Character.isSurrogate(current)) {
                bytes++;
            } else {
                bytes += 3L;
            }
        }
        return bytes;
    }
}
