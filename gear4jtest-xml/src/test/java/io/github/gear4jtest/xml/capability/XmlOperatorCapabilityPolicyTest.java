package io.github.gear4jtest.xml.capability;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.external.api.ExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlOperatorCapabilityPolicyTest {

    @Test
    void resolve_shouldKeepTestAndRunMappingsIndependent() {
        // Given
        XmlOperatorCapabilityPolicy policy = XmlOperatorCapabilityPolicy.builder()
                .allow("customer.normalize", TestOperator.class, ExecutionMode.TEST)
                .allow("customer.normalize", RunOperator.class, ExecutionMode.RUN)
                .build();

        // When / Then
        assertThat(policy.resolve("customer.normalize", ExecutionMode.TEST)).isEqualTo(TestOperator.class.getName());
        assertThat(policy.resolve("customer.normalize", ExecutionMode.RUN)).isEqualTo(RunOperator.class.getName());
    }

    @Test
    void resolve_shouldRejectUnknownCapabilitiesAndClassNameBypasses() {
        // Given
        XmlOperatorCapabilityPolicy policy = XmlOperatorCapabilityPolicy.builder()
                .allow("customer.normalize", RunOperator.class, ExecutionMode.RUN)
                .build();

        // When / Then
        assertThatThrownBy(() -> policy.resolve("customer.delete-all", ExecutionMode.RUN))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("customer.delete-all")
                .hasMessageContaining("RUN");
        assertThatThrownBy(() -> policy.resolve(RunOperator.class.getName(), ExecutionMode.RUN))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(RunOperator.class.getName());
    }

    @Test
    void builder_shouldRejectConflictingMappingsForOneMode() {
        // Given
        XmlOperatorCapabilityPolicy.Builder builder = XmlOperatorCapabilityPolicy.builder()
                .allow("customer.normalize", TestOperator.class, ExecutionMode.TEST);

        // When / Then
        assertThatThrownBy(() -> builder.allow("customer.normalize", RunOperator.class, ExecutionMode.TEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already mapped")
                .hasMessageContaining("TEST");
    }

    @Test
    void trustedClassNames_shouldRemainExplicit() {
        // Given
        XmlOperatorCapabilityPolicy policy = XmlOperatorCapabilityPolicy.trustedClassNames();

        // When / Then
        assertThat(policy.resolve(RunOperator.class.getName(), ExecutionMode.RUN))
                .isEqualTo(RunOperator.class.getName());
        assertThatThrownBy(() -> policy.resolve("not a class name", ExecutionMode.RUN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static final class TestOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext context) {
            return input;
        }
    }

    static final class RunOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext context) {
            return input;
        }
    }
}
