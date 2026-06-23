package io.github.gear4jtest.core.api.station;

import java.util.List;

import io.github.gear4jtest.core.api.MutableStationMetadata;
import io.github.gear4jtest.core.api.behavior.SkipPhase;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractStationTest {
    @Test
    void constructor_shouldExposeImmutableSkippers() {
        // Given
        TestStation station = new TestStation("station-1",
                List.of(StationSkipper.pre((input, ctx) -> "skip".equals(input))));

        // Then
        assertThat(station.getSkippers()).hasSize(1);
        assertThat(station.getSkippers().get(0).phase()).isEqualTo(SkipPhase.PRE_PROCESSORS);
        assertThat(station.getSkippers().get(0).shouldSkip("skip", null).shouldSkip()).isTrue();
        assertThat(station.getSkippers().get(0).shouldSkip("run", null).shouldSkip()).isFalse();
        assertThatThrownBy(() -> station.getSkippers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_shouldExposeTypedMetadata() {
        // Given
        MutableStationMetadata metadata = new MutableStationMetadata().put(String.class, "metadata-value");
        TestStation station = new TestStation("station-1", List.of(), metadata.immutableCopy());

        // Then
        assertThat(station.getMetadata().get(String.class)).contains("metadata-value");
        assertThat(station.getMetadata().require(String.class)).isEqualTo("metadata-value");
    }

    @Test
    void requireMetadata_shouldFailWhenValueIsMissing() {
        // Given
        TestStation station = new TestStation("station-1", List.of());

        var metadata = station.getMetadata();

        // When / Then
        assertThatThrownBy(() -> metadata.require(String.class)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.class.getName());
    }

    private static final class TestStation extends AbstractStation<String, String> {
        private TestStation(String id, List<StationSkipper> skippers) {
            super(id, StationKind.CUSTOM, null, null, null, false, skippers, null);
        }

        private TestStation(String id,
                            List<StationSkipper> skippers,
                            io.github.gear4jtest.core.api.StationMetadata metadata) {
            super(id, StationKind.CUSTOM, null, null, null, false, skippers, metadata);
        }
    }
}
