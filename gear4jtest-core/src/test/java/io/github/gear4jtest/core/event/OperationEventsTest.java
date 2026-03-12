package io.github.gear4jtest.core.event;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationEventsTest {

    @Test
    void operationBaseEvent_shouldExtendEventAndStorePipelineAndExecution() {
        UUID executionId = UUID.randomUUID();
        OperationBaseEvent event = new OperationBaseEvent(
                "pipeline-1",
                executionId,
                "CUSTOM_TYPE",
                "operation-1",
                "input"
        );

        assertThat(event).isInstanceOf(Event.class);
        assertThat(event.getPipelineId()).isEqualTo("pipeline-1");
        assertThat(event.getExecutionId()).isEqualTo("exec-1");
        assertThat(event.getName()).isEqualTo("CUSTOM_TYPE");
    }

    @Test
    void operationStartedEvent_shouldHaveTypeOperationStarted() {
        String pipelineId = "pipeline-1";
        UUID executionId = UUID.randomUUID();
        String operationId = "op-42";

        OperationStartedEvent event =
                new OperationStartedEvent(pipelineId, executionId, operationId, "input");

        assertThat(event.getPipelineId()).isEqualTo(pipelineId);
        assertThat(event.getExecutionId()).isEqualTo(executionId);
        // Comportement ATTENDU :
        assertThat(event.getName()).isEqualTo("OPERATION_STARTED");
    }

    @Test
    void operationCompletedEvent_shouldHaveTypeOperationCompleted() {
        UUID executionId = UUID.randomUUID();
        OperationCompletedEvent event =
                new OperationCompletedEvent("pipeline", executionId, "op", "in", "out");

        assertThat(event.getName()).isEqualTo("OPERATION_COMPLETED");
    }

    @Test
    void operationErrorEvent_shouldHaveTypeOperationError() {
        UUID executionId = UUID.randomUUID();
        Exception cause = new RuntimeException("boom");
        OperationErrorEvent event =
                new OperationErrorEvent("pipeline", executionId, "op", "input", cause);

        assertThat(event.getName()).isEqualTo("OPERATION_ERROR");
        // Pas de getter sur exception pour l’instant, donc on ne peut pas l’asserter proprement.
    }
}
