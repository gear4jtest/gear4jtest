package io.github.gear4jtest.xml.expression;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GearExpressionNumericEqualityTest {
    @ParameterizedTest(name = "{0} equals {1}")
    @MethodSource("equivalentSupportedNumbers")
    void evaluate_shouldCompareSupportedNumberWrappersByValue(Number left, Number right) {
        // Given
        GearExpressionContext context = numericContext(left, right);

        // When / Then
        assertThat(evaluate("variables.left == variables.right", context)).isTrue();
        assertThat(evaluate("variables.left != variables.right", context)).isFalse();
    }

    @ParameterizedTest(name = "{0} differs from {1}")
    @MethodSource("differentSupportedNumbers")
    void evaluate_shouldRejectDifferentFiniteNumericValues(Number left, Number right) {
        // Given
        GearExpressionContext context = numericContext(left, right);

        // When / Then
        assertThat(evaluate("variables.left == variables.right", context)).isFalse();
        assertThat(evaluate("variables.left != variables.right", context)).isTrue();
    }

    @Test
    void evaluate_shouldIgnoreDecimalScaleAndFloatingWrapperType() {
        // Given
        GearExpressionContext context = new GearExpressionContext(null,
                Map.of("floatValue", Float.valueOf(0.1F), "doubleValue", Double.valueOf(0.1D), "decimalValue",
                       new BigDecimal("0.1000")));

        // When / Then
        assertThat(evaluate("variables.floatValue == variables.doubleValue", context)).isTrue();
        assertThat(evaluate("variables.doubleValue == variables.decimalValue", context)).isTrue();
    }

    @Test
    void evaluate_shouldTreatPositiveAndNegativeZeroAsEqual() {
        // Given
        List<Number> zeros = List.of(Float.valueOf(+0.0F), Float.valueOf(-0.0F), Double.valueOf(+0.0D),
                                     Double.valueOf(-0.0D), Integer.valueOf(0), new BigDecimal("0.000"));

        // When / Then
        for (Number left : zeros) {
            for (Number right : zeros) {
                assertThat(evaluate("variables.left == variables.right", numericContext(left, right)))
                        .as("%s must equal %s", left, right)
                        .isTrue();
            }
        }
    }

    @ParameterizedTest(name = "NaN operand {0} does not equal {1}")
    @MethodSource("comparisonsContainingNaN")
    void evaluate_shouldTreatNaNAsUnequalToEveryNumber(Number left, Number right) {
        // Given
        GearExpressionContext context = numericContext(left, right);

        // When / Then
        assertThat(evaluate("variables.left == variables.right", context)).isFalse();
        assertThat(evaluate("variables.left != variables.right", context)).isTrue();
    }

    @ParameterizedTest(name = "infinity comparison: {0}, {1} -> {2}")
    @MethodSource("infinityComparisons")
    void evaluate_shouldCompareInfinitiesOnlyBySign(Number left, Number right, boolean expectedEqual) {
        // Given
        GearExpressionContext context = numericContext(left, right);

        // When / Then
        assertThat(evaluate("variables.left == variables.right", context)).isEqualTo(expectedEqual);
        assertThat(evaluate("variables.left != variables.right", context)).isEqualTo(!expectedEqual);
    }

    @Test
    void evaluate_shouldCompareNumericLiteralsAcrossIntegerAndDecimalForms() {
        // When / Then
        assertThat(evaluate("1 == 1.0", GearExpressionContext.ofInput(null))).isTrue();
        assertThat(evaluate("1 != 1.00", GearExpressionContext.ofInput(null))).isFalse();
    }

    @Test
    void evaluate_shouldKeepNonNumericScalarEqualityUnchanged() {
        // Given
        GearExpressionContext context = new GearExpressionContext(null,
                Map.of("leftCharacter", Character.valueOf('a'), "rightCharacter", Character.valueOf('a'),
                       "otherCharacter", Character.valueOf('b')));

        // When / Then
        assertThat(evaluate("variables.leftCharacter == variables.rightCharacter", context)).isTrue();
        assertThat(evaluate("variables.leftCharacter == variables.otherCharacter", context)).isFalse();
        assertThat(evaluate("'1' == 1", context)).isFalse();
    }

    @Test
    void evaluate_shouldRejectNumberSubclassWithoutInvokingApplicationMethods() {
        // Given
        SideEffectBigDecimal value = new SideEffectBigDecimal("1");
        GearExpressionContext context = new GearExpressionContext(null, Map.of("value", value));
        GearExpression expression = GearExpressionParser.parse("variables.value == 1");

        // When / Then
        assertThatThrownBy(() -> expression.evaluate(context))
                .isInstanceOf(GearExpressionException.class)
                .hasMessageContaining("inert scalar");
        assertThat(value.toStringCalls()).as("GEL must not invoke methods on application Number subclasses").isZero();
    }

    private static Stream<Arguments> equivalentSupportedNumbers() {
        List<Number> values = List.of(Byte.valueOf((byte) 1), Short.valueOf((short) 1), Integer.valueOf(1),
                                      Long.valueOf(1L), BigInteger.ONE, Float.valueOf(1.0F), Double.valueOf(1.0D),
                                      new BigDecimal("1.000"));
        return values.stream().flatMap(left -> values.stream().map(right -> Arguments.of(left, right)));
    }

    private static Stream<Arguments> differentSupportedNumbers() {
        return Stream.of(Arguments.of(Byte.valueOf((byte) 1), Short.valueOf((short) 2)),
                         Arguments.of(Integer.valueOf(-1), Long.valueOf(1L)),
                         Arguments.of(BigInteger.valueOf(Long.MAX_VALUE), new BigDecimal("9223372036854775806.999")),
                         Arguments.of(Float.valueOf(0.1F), Double.valueOf(0.2D)));
    }

    private static Stream<Arguments> comparisonsContainingNaN() {
        return Stream.of(Arguments.of(Double.NaN, Double.NaN), Arguments.of(Float.NaN, Float.NaN),
                         Arguments.of(Double.NaN, Float.NaN), Arguments.of(Double.NaN, Integer.valueOf(1)),
                         Arguments.of(Integer.valueOf(1), Float.NaN));
    }

    private static Stream<Arguments> infinityComparisons() {
        return Stream.of(Arguments.of(Double.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, true),
                         Arguments.of(Double.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, true),
                         Arguments.of(Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, false),
                         Arguments.of(Float.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false),
                         Arguments.of(Double.POSITIVE_INFINITY, Double.valueOf(Double.MAX_VALUE), false),
                         Arguments.of(new BigDecimal("1E+10000"), Float.POSITIVE_INFINITY, false));
    }

    private static GearExpressionContext numericContext(Number left, Number right) {
        return new GearExpressionContext(null, Map.of("left", left, "right", right));
    }

    private static boolean evaluate(String expression, GearExpressionContext context) {
        return GearExpressionParser.parse(expression).evaluateBoolean(context);
    }

    private static final class SideEffectBigDecimal extends BigDecimal {
        private static final long serialVersionUID = 1L;
        private int toStringCalls;

        SideEffectBigDecimal(String value) {
            super(value);
        }

        @Override
        public String toString() {
            toStringCalls++;
            return super.toString();
        }

        int toStringCalls() {
            return toStringCalls;
        }
    }
}
