package io.github.gear4jtest.core.spi.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.trace.StationTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractStationHooksExtensionTest {
    @Test
    void wrapStationRunner_shouldCallSuccessHooksAroundDelegate() {
        // Given
        RecordingStationHooksExtension extension = new RecordingStationHooksExtension();
        StationLogTrace trace = StationLogTrace.start(UUID.randomUUID(), "operation", null);
        StationRunner runner = extension.wrapStationRunner((input, station, ctx) -> trace, null);

        // When
        StationTrace actual = runner.run("input", null, null);

        // Then
        assertThat(actual).isSameAs(trace);
        assertThat(extension.events).containsExactly("start", "result:operation", "end");
    }

    @Test
    void wrapStationRunner_shouldCallExceptionAndEndHooksWhenDelegateFails() {
        // Given
        RecordingStationHooksExtension extension = new RecordingStationHooksExtension();
        IllegalStateException failure = new IllegalStateException("boom");
        StationRunner runner = extension.wrapStationRunner((input, station, ctx) -> {
            throw failure;
        }, null);

        // When / Then
        assertThatThrownBy(() -> runner.run("input", null, null)).isSameAs(failure);
        assertThat(extension.events).containsExactly("start", "exception:boom", "end");
    }

    private static final class RecordingStationHooksExtension extends AbstractStationHooksExtension {
        private final List<String> events = new ArrayList<>();

        @Override
        protected void onStart(AbstractStation<?, ?> station, StationExecutionContext stationCtx) {
            events.add("start");
        }

        @Override
        protected void onResult(AbstractStation<?, ?> station,
                                StationExecutionContext stationCtx,
                                StationTrace log) {
            events.add("result:" + log.getOperationId());
        }

        @Override
        protected void onException(AbstractStation<?, ?> station,
                                   StationExecutionContext stationCtx,
                                   RuntimeException error) {
            events.add("exception:" + error.getMessage());
        }

        @Override
        protected void onEnd(AbstractStation<?, ?> station, StationExecutionContext stationCtx) {
            events.add("end");
        }
    }
}
