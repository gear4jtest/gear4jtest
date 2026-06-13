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
}
