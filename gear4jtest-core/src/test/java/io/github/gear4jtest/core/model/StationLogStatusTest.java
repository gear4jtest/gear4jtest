package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.persistence.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationLogStatusTest {
    @Test
    void stationLogStatus_shouldExposeCorrespondingExecutionStatus() {
        assertThat(StationLogStatus.SUCCEEDED.toExecutionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(StationLogStatus.RUNNING.toExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    }
}
