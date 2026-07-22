package io.github.gear4jtest.core.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FlowConfigTest {
    @Test
    void constructor_shouldRejectNullPolicies() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FlowConfig(null, StopPolicy.PROPAGATE_STOP, CancelPolicy.PROPAGATE_CANCEL))
                .withMessage("failurePolicy must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new FlowConfig(FailurePolicy.FAIL_FAST, null, CancelPolicy.PROPAGATE_CANCEL))
                .withMessage("stopPolicy must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new FlowConfig(FailurePolicy.FAIL_FAST, StopPolicy.PROPAGATE_STOP, null))
                .withMessage("cancelPolicy must not be null");
    }
}
