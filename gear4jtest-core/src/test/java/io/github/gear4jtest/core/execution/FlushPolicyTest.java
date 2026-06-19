package io.github.gear4jtest.core.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlushPolicyTest {
    @Test
    void byCount_shouldCreateCountPolicy() {
        FlushPolicy policy = FlushPolicy.byCount(25);

        assertThat(policy.type()).isEqualTo(FlushPolicy.Type.BY_COUNT);
        assertThat(policy.count()).isEqualTo(25);
        assertThat(policy.every()).isNull();
        assertThat(policy.approxBytes()).isZero();
    }

    @Test
    void byCount_shouldRejectNonPositiveCount() {
        assertThatThrownBy(() -> FlushPolicy.byCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be > 0");
        assertThatThrownBy(() -> FlushPolicy.byCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be > 0");
    }
}
