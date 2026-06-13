package io.github.gear4jtest.xml.expression;

import java.util.Map;

public record GearExpressionContext(Object input, Map<String, ?> variables) {
    public GearExpressionContext {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static GearExpressionContext ofInput(Object input) {
        return new GearExpressionContext(input, Map.of());
    }
}
