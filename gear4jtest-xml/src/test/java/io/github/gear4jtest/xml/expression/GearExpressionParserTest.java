package io.github.gear4jtest.xml.expression;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GearExpressionParserTest {
    @Test
    void evaluateBoolean_shouldResolveInputAndVariablesWithoutExecutingJavaCode() {
        // Given
        PropertyAccessPolicy accessPolicy = PropertyAccessPolicy.allowlist().allowRecordType(Document.class).build();
        GearExpression expression = GearExpressionParser
                .parse("input.productType == 'BOOK' && variables.enabled && input.active");

        // When
        boolean result = expression.evaluateBoolean(
                                                    new GearExpressionContext(new Document("BOOK", true),
                                                            Map.of("enabled", true), accessPolicy));

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
        GearExpression expression = GearExpressionParser.parse("input.class.name == 'Document'");

        GearExpressionContext context = new GearExpressionContext(new SimpleDocument("BOOK"), Map.of());

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
                .as("GEL property paths must not expose Java Class metadata through getClass()")
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("class metadata");
    }

    @Test
    void evaluate_shouldRejectDirectObjectMethodsAsProperties() {
        // Given
        GearExpression expression = GearExpressionParser.parse("input.toString == 'BOOK'");

        GearExpressionContext context = new GearExpressionContext(new SimpleDocument("BOOK"), Map.of());

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
    void evaluate_shouldReadTheContextSnapshotInsteadOfCallingTheSourceMap() {
        // Given
        SideEffectMap source = new SideEffectMap();
        source.put("productType", "BOOK");
        GearExpressionContext context = GearExpressionContext.ofInput(source);
        GearExpression expression = GearExpressionParser.parse("input.productType == 'BOOK'");

        // When
        boolean result = expression.evaluateBoolean(context);

        // Then
        assertThat(result).isTrue();
        assertThat(source.getCalls()).as("expression evaluation must not call an application Map implementation")
                .isZero();
    }

    @Test
    void evaluate_shouldRejectEqualityOnApplicationObjectsWithoutCallingEquals() {
        // Given
        SideEffectEquality value = new SideEffectEquality();
        GearExpressionContext context = GearExpressionContext.ofInput(Map.of("value", value));
        GearExpression expression = GearExpressionParser.parse("input.value == input.value");

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("inert scalar");
        assertThat(value.equalsCalls()).as("GEL must not invoke application equals implementations").isZero();
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
                .hasMessageContaining("not allowlisted");
        assertThat(document.calls()).as("the arbitrary method must not be invoked as a pseudo property")
                .isZero();
    }

    @Test
    void evaluate_shouldRejectJavaBeanGetterByDefaultWithoutInvokingIt() {
        // Given
        SideEffectBean document = new SideEffectBean();
        GearExpression expression = GearExpressionParser.parse("input.secret == 'secret'");

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(GearExpressionContext.ofInput(document)))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("not allowlisted");
        assertThat(document.calls()).as("the denied getter must not run").isZero();
    }

    @Test
    void evaluate_shouldAllowOnlyExplicitlyAllowlistedBeanProperty() {
        // Given
        BeanDocument document = new BeanDocument("BOOK", true);
        PropertyAccessPolicy accessPolicy = PropertyAccessPolicy.allowlist()
                .allowProperty(BeanDocument.class, "productType")
                .build();
        GearExpression allowed = GearExpressionParser.parse("input.productType == 'BOOK'");
        GearExpression denied = GearExpressionParser.parse("input.active");
        GearExpressionContext context = GearExpressionContext.ofInput(document, accessPolicy);

        // When / Then
        assertThat(allowed.evaluateBoolean(context)).isTrue();
        assertThatThrownBy(() -> denied.evaluate(context))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void evaluate_shouldNotApplyAnAllowlistToUnknownSubclasses() {
        // Given
        PropertyAccessPolicy accessPolicy = PropertyAccessPolicy.allowlist()
                .allowProperty(BeanDocument.class, "productType")
                .build();
        GearExpression expression = GearExpressionParser.parse("input.productType == 'BOOK'");

        // When / Then
        assertThatThrownBy(
                           () -> expression.evaluate(GearExpressionContext.ofInput(new ExtendedBeanDocument("BOOK"),
                                                                                   accessPolicy)))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void evaluate_shouldAllowAnApprovedRecordSnapshotThroughSecureMapAccess() {
        // Given
        Document document = new Document("BOOK", true);
        PropertyAccessPolicy snapshotPolicy = PropertyAccessPolicy.allowlist().allowRecordType(Document.class).build();
        Object inertInput = GearExpressionValues.snapshot(document, snapshotPolicy);
        GearExpression expression = GearExpressionParser.parse("input.productType == 'BOOK' && input.active");

        // When
        boolean result = expression.evaluateBoolean(GearExpressionContext.ofInput(inertInput));

        // Then
        assertThat(result).isTrue();
        assertThat(inertInput).isInstanceOf(Map.class);
    }

    @Test
    void snapshot_shouldRejectCycles() {
        // Given
        Map<String, Object> cyclic = new java.util.HashMap<>();
        cyclic.put("self", cyclic);

        // When / Then
        assertThatThrownBy(() -> GearExpressionValues.snapshot(cyclic))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("cycle");
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
    @SuppressWarnings("removal")
    void evaluate_shouldProvideTemporaryLegacyBeanCompatibility() {
        // Given
        BeanDocument document = new BeanDocument("BOOK", true);
        GearExpression expression = GearExpressionParser.parse("input.productType == 'BOOK' && input.active");

        // When
        boolean result = expression.evaluateBoolean(GearExpressionContext.legacy(document, Map.of()));

        // Then
        assertThat(result).isTrue();
    }

    public record Document(String productType, boolean active) {}

    public record SimpleDocument(String productType) {}

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

    public static class BeanDocument {
        private final String productType;
        private final boolean active;

        public BeanDocument(String productType, boolean active) {
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

    public static final class ExtendedBeanDocument extends BeanDocument {
        public ExtendedBeanDocument(String productType) {
            super(productType, true);
        }
    }

    public static final class SideEffectBean {
        private int calls;

        public String getSecret() {
            calls++;
            return "secret";
        }

        int calls() {
            return calls;
        }
    }

    public static final class SideEffectMap extends java.util.HashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        private int getCalls;

        @Override
        public Object get(Object key) {
            getCalls++;
            return super.get(key);
        }

        int getCalls() {
            return getCalls;
        }
    }

    public static final class SideEffectEquality {
        private int equalsCalls;

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        int equalsCalls() {
            return equalsCalls;
        }
    }
}
