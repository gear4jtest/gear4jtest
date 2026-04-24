package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.model.StationLogStatus;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.persistence.StationLogRecord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StationEventsTest {

    @Test
    void stationStartedEvent_shouldExposeCorrelationFields() {
        UUID executionId = UUID.randomUUID();
        UUID stationExecutionId = UUID.randomUUID();
        UUID parentOperationId = UUID.randomUUID();

        StationStartedEvent event = new StationStartedEvent(
                "pipeline-1",
                executionId,
                stationExecutionId,
                "operation-1",
                parentOperationId,
                "item-42",
                "input");

        assertThat(event.getPipelineId()).isEqualTo("pipeline-1");
        assertThat(event.getExecutionId()).isEqualTo(executionId);
        assertThat(event.getStationExecutionId()).isEqualTo(stationExecutionId);
        assertThat(event.getOperationId()).isEqualTo("operation-1");
        assertThat(event.getParentOperationId()).isEqualTo(parentOperationId);
        assertThat(event.getItemId()).isEqualTo("item-42");
        assertThat(event.getInput()).isEqualTo("input");
        assertThat(event.getName()).isEqualTo("StationStartedEvent");
    }

    @Test
    void stationFinishedEvent_shouldExposeFinalStatusAndError() {
        RuntimeException boom = new RuntimeException("boom");
        StationFinishedEvent event = new StationFinishedEvent(
                "pipeline-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "operation-1",
                null,
                "item-42",
                "input",
                StationLogStatus.FAILED,
                null,
                boom);

        assertThat(event.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(event.getError()).isSameAs(boom);
        assertThat(event.isSuccessful()).isFalse();
    }

    @Test
    void parameterResolvedEvent_shouldExposeResolutionMetadata() {
        ParameterResolvedEvent event = new ParameterResolvedEvent(
                "pipeline-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "operation-1",
                null,
                "item-42",
                "customer-param",
                true,
                String.class.getName());

        assertThat(event.getParameterDescriptor()).isEqualTo("customer-param");
        assertThat(event.isCacheHit()).isTrue();
        assertThat(event.getValueType()).isEqualTo(String.class.getName());
    }
}
