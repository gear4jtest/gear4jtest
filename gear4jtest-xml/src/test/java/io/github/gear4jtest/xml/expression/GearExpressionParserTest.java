package io.github.gear4jtest.xml.expression;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GearExpressionParserTest {
    @Test
    void evaluateBoolean_shouldResolveInputAndVariablesWithoutExecutingJavaCode() {
        // Given
        record Document(String productType, boolean active) {}
        GearExpression expression = GearExpressionParser
                .parse("input.productType == 'BOOK' && variables.enabled && input.active");

        // When
        boolean result = expression
                .evaluateBoolean(new GearExpressionContext(new Document("BOOK", true), Map.of("enabled", true)));

        // Then
        assertThat(result).as("safe GEL expression resolves only data properties and boolean operators").isTrue();
    }

    @Test
    void parse_shouldRejectJavaLikeMethodInvocationSyntax() {
        // When / Then
        assertThatThrownBy(() -> GearExpressionParser.parse("T(java.lang.System).exit(0)"))
                .as("GEL is not SpEL/Java and must reject type lookup or method invocation syntax")
                .isInstanceOf(GearExpressionException.class);
    }

    @Test
    void evaluate_shouldRejectJavaClassMetadataAccessOnObjects() {
        // Given
        record Document(String productType) {}
        GearExpression expression = GearExpressionParser.parse("input.class.name == 'Document'");

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(new GearExpressionContext(new Document("BOOK"), Map.of())))
                .as("GEL property paths must not expose Java Class metadata through getClass()")
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("class metadata");
    }

    @Test
    void evaluate_shouldRejectDirectObjectMethodsAsProperties() {
        // Given
        record Document(String productType) {}
        GearExpression expression = GearExpressionParser.parse("input.toString == 'BOOK'");

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(new GearExpressionContext(new Document("BOOK"), Map.of())))
                .as("GEL property paths must not invoke Object methods as pseudo properties")
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("No readable property");
    }

    @Test
    void evaluate_shouldStillAllowMapKeysNamedClassAsData() {
        // Given
        GearExpression expression = GearExpressionParser.parse("input.class == 'BOOK'");

        // When
        boolean result = expression.evaluateBoolean(new GearExpressionContext(Map.of("class", "BOOK"), Map.of()));

        // Then
        assertThat(result).as("map keys are data, not reflective Java class metadata").isTrue();
    }

}
