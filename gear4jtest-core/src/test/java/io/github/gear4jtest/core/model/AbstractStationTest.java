package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.api.behavior.SkipPhase;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractStationTest {
    @Test
    void addSkipper_shouldIgnoreNullSkipper() {
        // Given
        TestStation station = new TestStation("station-1");

        // When
        station.addSkipper(null);

        // Then
        assertThat(station.getSkippers()).isEmpty();
    }

    @Test
    void skipIf_shouldRegisterPreProcessorSkipper() {
        // Given
        TestStation station = new TestStation("station-1");

        // When
        station.skipIf((input, ctx) -> "skip".equals(input));

        // Then
        assertThat(station.getSkippers()).hasSize(1);
        assertThat(station.getSkippers().get(0).phase()).isEqualTo(SkipPhase.PRE_PROCESSORS);
        assertThat(station.getSkippers().get(0).shouldSkip("skip", null).shouldSkip()).isTrue();
        assertThat(station.getSkippers().get(0).shouldSkip("run", null).shouldSkip()).isFalse();
    }

    @Test
    void skipIfPost_shouldRegisterPostProcessorSkipper() {
        // Given
        TestStation station = new TestStation("station-1");

        // When
        station.skipIfPost((input, ctx) -> true);

        // Then
        assertThat(station.getSkippers()).hasSize(1);
        assertThat(station.getSkippers().get(0).phase()).isEqualTo(SkipPhase.POST_PROCESSORS);
    }

    @Test
    void putMetadata_shouldExposeTypedMetadata() {
        // Given
        TestStation station = new TestStation("station-1");

        // When
        station.putMetadata(String.class, "metadata-value");

        // Then
        assertThat(station.getMetadata().get(String.class)).contains("metadata-value");
        assertThat(station.getMetadata().require(String.class)).isEqualTo("metadata-value");
    }

    @Test
    void requireMetadata_shouldFailWhenValueIsMissing() {
        // Given
        TestStation station = new TestStation("station-1");

        var metadata = station.getMetadata();

        // When / Then
        assertThatThrownBy(() -> metadata.require(String.class)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.class.getName());
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation(String id) {
            super(id, StationKind.OTHER);
        }
    }
}
