package io.github.gear4jtest.xml.expression;

/**
 * Safe Gear4J expression contract. This API is intentionally small and does not
 * evaluate arbitrary Java code: no method invocation syntax, no type lookup, no
 * object creation and no static access are supported.
 */
@FunctionalInterface
public interface GearExpression {
    Object evaluate(GearExpressionContext context);

    default boolean evaluateBoolean(GearExpressionContext context) {
        Object value = evaluate(context);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        throw new GearExpressionException("Expression did not evaluate to a boolean: " + value.getClass().getName());
    }
}
