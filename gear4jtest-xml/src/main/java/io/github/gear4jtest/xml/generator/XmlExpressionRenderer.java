package io.github.gear4jtest.xml.generator;

import java.util.Objects;

import io.github.gear4jtest.xml.expression.GearExpressionParser;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Action;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ValueParameter;

/**
 * Renders XML expressions into generated Java source fragments.
 *
 * <p>
 * Java snippets are delegated to {@link XmlJavaSourcePolicy}. GEL conditions
 * are parsed at generation time and rendered through a generated allowlisted
 * helper.
 * </p>
 */
final class XmlExpressionRenderer {
    private final XmlJavaSourcePolicy sourcePolicy;

    XmlExpressionRenderer(XmlJavaSourcePolicy sourcePolicy) {
        this.sourcePolicy = Objects.requireNonNull(sourcePolicy, "sourcePolicy must not be null");
    }

    String valueExpression(ValueParameter parameter) {
        if ("java.lang.String".equals(parameter.valueType()) || "String".equals(parameter.valueType())) {
            return "\"" + JavaStringEscaper.escapeJava(parameter.value()) + "\"";
        }
        sourcePolicy.validateJavaExpression(parameter.value());
        return parameter.value();
    }

    String conditionLambda(Condition condition, JavaImportManager imports) {
        if (condition.isGel()) {
            validateGel(condition.expression());
            return "(input, ctx) -> evaluateGel(\"" + JavaStringEscaper.escapeJava(condition.expression())
                    + "\", input, ctx)";
        }
        requireKnownLanguage(condition);
        String expression = normalizeExpression(condition.expression().trim(), imports);
        if (expression.contains("->")) {
            return expression;
        }
        return "(input, ctx) -> " + expression;
    }

    String signalConditionLambda(Condition condition, JavaTypeName inputType, JavaImportManager imports) {
        if (condition == null) {
            return "sig -> true";
        }
        if (condition.isGel()) {
            validateGel(condition.expression());
            return "sig -> evaluateGel(\"" + JavaStringEscaper.escapeJava(condition.expression())
                    + "\", sig.getItem(), sig.getItemExecution())";
        }

        requireKnownLanguage(condition);
        String expression = normalizeExpression(condition.expression().trim(), imports);
        if (expression.contains("->")) {
            return expression;
        }

        String type = inputType.render(imports);
        return "sig -> { " + type + " input = sig.getItem(); " + "var ctx = sig.getItemExecution(); " + "return "
                + expression + "; " + "}";
    }

    String actionStatement(Action action, JavaImportManager imports) {
        String statement = normalizeExpression(action.expression().trim(), imports);
        return statement.endsWith(";") ? statement : statement + ";";
    }

    String normalizeRetriever(String retriever, JavaTypeName operationType, JavaImportManager imports) {
        if (retriever == null) {
            return null;
        }
        sourcePolicy.validateJavaExpression(retriever);
        return retriever.replace(operationType.canonical() + "::", operationType.render(imports) + "::");
    }

    String normalizeExpression(String expression, JavaImportManager imports) {
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

    private static void validateGel(String expression) {
        GearExpressionParser.parse(expression);
    }

    private static void requireKnownLanguage(Condition condition) {
        if (!Condition.LANGUAGE_JAVA.equals(condition.language())) {
            throw new IllegalArgumentException("Unsupported XML condition language: " + condition.language());
        }
    }

    private static String replaceTypeReference(String expression,
                                               JavaImportManager imports,
                                               String fullyQualifiedType) {
        if (!expression.contains(fullyQualifiedType)) {
            return expression;
        }
        return expression.replace(fullyQualifiedType, imports.use(fullyQualifiedType));
    }
}
