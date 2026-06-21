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

        GearExpressionContext context = new GearExpressionContext(new Document("BOOK"), Map.of());

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
                .as("GEL property paths must not expose Java Class metadata through getClass()")
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("class metadata");
    }

    @Test
    void evaluate_shouldRejectDirectObjectMethodsAsProperties() {
        // Given
        record Document(String productType) {}
        GearExpression expression = GearExpressionParser.parse("input.toString == 'BOOK'");

        GearExpressionContext context = new GearExpressionContext(new Document("BOOK"), Map.of());

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
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

    @Test
    void evaluate_shouldRejectZeroArgumentMethodsThatAreNotRecordComponentsOrBeanGetters() {
        // Given
        DangerousDocument document = new DangerousDocument();
        GearExpression expression = GearExpressionParser.parse("input.expensiveComputation == 'secret'");

        GearExpressionContext context = new GearExpressionContext(document, Map.of());

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
                .as("GEL property access must not call arbitrary zero-argument methods")
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("No readable property");
        assertThat(document.calls()).as("the arbitrary method must not be invoked as a pseudo property")
                .isZero();
    }

    @Test
    void parse_shouldRejectExpressionThatExceedsMaxLength() {
        // Given
        String expression = "a".repeat(GearExpressionParser.DEFAULT_MAX_EXPRESSION_LENGTH + 1);

        // When / Then
        assertThatThrownBy(() -> GearExpressionParser.parse(expression))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("max length");
    }

    @Test
    void parse_shouldRejectExpressionThatExceedsMaxTokenCount() {
        // Given
        String expression = ("true || ").repeat(300) + "true";

        // When / Then
        assertThatThrownBy(() -> GearExpressionParser.parse(expression))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("max token count");
    }

    @Test
    void parse_shouldRejectPathThatExceedsMaxSegments() {
        // Given
        String expression = "input" + ".child".repeat(GearExpressionParser.DEFAULT_MAX_PATH_SEGMENTS);

        // When / Then
        assertThatThrownBy(() -> GearExpressionParser.parse(expression))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("max segments");
    }

    @Test
    void parse_shouldRejectExpressionThatExceedsMaxNestingDepth() {
        // Given
        String expression = "(".repeat(GearExpressionParser.DEFAULT_MAX_NESTING_DEPTH + 1)
                + "true"
                + ")".repeat(GearExpressionParser.DEFAULT_MAX_NESTING_DEPTH + 1);

        // When / Then
        assertThatThrownBy(() -> GearExpressionParser.parse(expression))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("max nesting depth");
    }

    @Test
    void evaluate_shouldAllowJavaBeanGettersOnPojoObjects() {
        // Given
        BeanDocument document = new BeanDocument("BOOK", true);
        GearExpression expression = GearExpressionParser.parse("input.productType == 'BOOK' && input.active");

        // When
        boolean result = expression.evaluateBoolean(new GearExpressionContext(document, Map.of()));

        // Then
        assertThat(result).isTrue();
    }

    private static final class DangerousDocument {
        private int calls;

        public String expensiveComputation() {
            calls++;
            return "secret";
        }

        private int calls() {
            return calls;
        }
    }

    private static final class BeanDocument {
        private final String productType;
        private final boolean active;

        private BeanDocument(String productType, boolean active) {
            this.productType = productType;
            this.active = active;
        }

        public String getProductType() {
            return productType;
        }

        public boolean isActive() {
            return active;
        }
    }
}
