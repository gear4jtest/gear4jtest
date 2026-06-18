package io.github.gear4jtest.xml.generator;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Controls whether XML definitions may inject Java source expressions into the
 * generated pipeline class.
 *
 * <p>
 * XML definitions are code when they contain Java expressions. Use
 * {@link #trusted()} only for definitions produced by trusted authors or
 * trusted tooling. Use {@link #forbidInlineJava()} for untrusted XML; it
 * rejects inline expressions and forces the caller to use a safer DSL/whitelist
 * layer before generation.
 * </p>
 */
public interface XmlJavaSourcePolicy {
    Pattern JAVA_PACKAGE = Pattern.compile("[a-zA-Z_$][a-zA-Z\\d_$]*(\\.[a-zA-Z_$][a-zA-Z\\d_$]*)*");

    default void validatePackageName(String packageName) {
        if (packageName == null || !JAVA_PACKAGE.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid Java package name: " + packageName);
        }
    }

    void validateJavaExpression(String expression);

    static XmlJavaSourcePolicy trusted() {
        return expression -> {
            // trusted source: expression is intentionally copied into generated Java
        };
    }

    static XmlJavaSourcePolicy forbidInlineJava() {
        return expression -> {
            if (expression != null && !expression.isBlank()) {
                throw new SecurityException("Inline Java expressions are not allowed for untrusted XML definitions "
                        + "(expressionLength=" + expression.length() + ")");
            }
        };
    }

    static XmlJavaSourcePolicy require(XmlJavaSourcePolicy policy) {
        return Objects.requireNonNull(policy, "sourcePolicy must not be null");
    }
}
