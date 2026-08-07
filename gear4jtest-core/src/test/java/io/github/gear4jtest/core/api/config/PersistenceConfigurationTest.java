package io.github.gear4jtest.core.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceConfigurationTest {
    @Test
    void defaults_shouldDelegateFlushThresholdToPersistenceManager() {
        PersistenceConfiguration configuration = PersistenceConfiguration.builder().build();

        assertThat(configuration.isStoreResultObject()).isTrue();
        assertThat(configuration.getStationLogFlushThreshold()).isEmpty();
    }

    @Test
    void toBuilder_shouldPreserveValuesWhileAllowingThresholdOverride() {
        PersistenceConfiguration original = PersistenceConfiguration.builder()
                .storeResultObject(false)
                .stationLogFlushThreshold(17)
                .build();

        PersistenceConfiguration copy = original.toBuilder().stationLogFlushThreshold(23).build();

        assertThat(copy.isStoreResultObject()).isFalse();
        assertThat(copy.getStationLogFlushThreshold()).hasValue(23);
        assertThat(original.getStationLogFlushThreshold()).hasValue(17);
    }

    @Test
    void stationLogFlushThreshold_shouldRejectNonPositiveValues() {
        assertThatThrownBy(() -> PersistenceConfiguration.builder().stationLogFlushThreshold(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stationLogFlushThreshold must be > 0");
    }
}
