package io.github.gear4jtest.xml.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyAccessorsTest {
    @Test
    void read_shouldRethrowJvmErrorsUnchanged() {
        // Given
        AssertionError fatalError = new AssertionError("fatal");
        PropertyAccessorsTestFixture bean = new PropertyAccessorsTestFixture(fatalError);

        // When / Then
        assertThatThrownBy(() -> PropertyAccessors.read(bean, "value")).isSameAs(fatalError);
    }

    @Test
    void read_shouldWrapOrdinaryAccessorFailures() {
        // Given
        IllegalStateException accessorFailure = new IllegalStateException("unavailable");
        PropertyAccessorsTestFixture bean = new PropertyAccessorsTestFixture(accessorFailure);

        // When / Then
        assertThatThrownBy(() -> PropertyAccessors.read(bean, "value"))
                .isInstanceOf(GearExpressionException.class)
                .hasCause(accessorFailure);
    }
}
