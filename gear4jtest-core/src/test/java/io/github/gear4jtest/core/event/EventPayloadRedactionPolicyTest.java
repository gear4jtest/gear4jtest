package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.spi.security.RedactionTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadRedactionPolicyTest {
    @Test
    void redacting_shouldApplyAfterPayloadSelection() {
        // Given
        EventPayloadPolicy policy = EventPayloadPolicy.redacting(EventPayloadPolicy.passthrough(),
                                                                 (target,
                                                                  value) -> target == RedactionTarget.EVENT_INPUT
                                                                          || target == RedactionTarget.EVENT_OUTPUT
                                                                                  ? "***" : value);

        // When / Then
        assertThat(policy.mapStationInput("secret", null)).isEqualTo("***");
        assertThat(policy.mapStationOutput("secret", null)).isEqualTo("***");
    }
}
