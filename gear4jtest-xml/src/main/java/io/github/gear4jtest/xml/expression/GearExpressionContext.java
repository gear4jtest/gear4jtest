package io.github.gear4jtest.xml.expression;

import java.util.Map;
import java.util.Objects;

public record GearExpressionContext(Object input,
                                    Map<String, ?> variables,
                                    PropertyAccessPolicy propertyAccessPolicy) {
    public GearExpressionContext(Object input, Map<String, ?> variables) {
        this(input, variables, PropertyAccessPolicy.secureDefaults());
    }

    public GearExpressionContext {
        propertyAccessPolicy = Objects.requireNonNull(propertyAccessPolicy, "propertyAccessPolicy");
        input = GearExpressionValues.snapshotMaps(input);
        Object inertVariables = GearExpressionValues.snapshotMaps(variables == null ? Map.of() : variables);
        @SuppressWarnings("unchecked")
        Map<String, ?> checkedVariables = (Map<String, ?>) inertVariables;
        variables = checkedVariables;
    }

    public static GearExpressionContext ofInput(Object input) {
        return new GearExpressionContext(input, Map.of());
    }

    public static GearExpressionContext ofInput(Object input, PropertyAccessPolicy propertyAccessPolicy) {
        return new GearExpressionContext(input, Map.of(), propertyAccessPolicy);
    }

    /**
     * Creates a temporary compatibility context that permits record and JavaBean
     * access and warns for every newly used accessor.
     *
     * @deprecated Configure an explicit allowlist or use
     *             {@link GearExpressionValues#snapshot(Object, PropertyAccessPolicy)}.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    public static GearExpressionContext legacy(Object input, Map<String, ?> variables) {
        return new GearExpressionContext(input, variables, PropertyAccessPolicy.legacyBeanAccess());
    }
}
