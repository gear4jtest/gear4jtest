package io.github.gear4jtest.core.api.assemblyline;

import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRequirementTest {
    @Test
    void factories_shouldCreateExpectedRequirementTypes() {
        assertThat(RuntimeRequirement.defaultEventHandling())
                .isEqualTo(new RuntimeRequirement(RuntimeRequirementType.EVENT_HANDLING, "default"));
        assertThat(RuntimeRequirement.eventHandling("audit"))
                .isEqualTo(new RuntimeRequirement(RuntimeRequirementType.EVENT_HANDLING, "audit"));
        assertThat(RuntimeRequirement.custom("feature-x"))
                .isEqualTo(new RuntimeRequirement(RuntimeRequirementType.CUSTOM, "feature-x"));
        assertThat(RuntimeRequirement.stationExtension(TestExtension.class))
                .isEqualTo(new RuntimeRequirement(RuntimeRequirementType.STATION_EXTENSION,
                        TestExtension.class.getName()));
    }

    @Test
    void constructor_shouldRejectInvalidValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RuntimeRequirement(null, "key"))
                .withMessage("type must not be null");
        assertThatThrownBy(() -> new RuntimeRequirement(RuntimeRequirementType.CUSTOM, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key must not be blank");
        assertThatNullPointerException()
                .isThrownBy(() -> RuntimeRequirement.stationExtension(null))
                .withMessage("extensionType must not be null");
    }

    private static final class TestExtension implements RuntimeExtension {
    }
}
