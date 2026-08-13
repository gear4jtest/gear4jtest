package io.github.gear4jtest.core.execution.trace;

import java.util.UUID;

import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationLogTraceTest {
    @Test
    void start_finishSuccess_shouldProduceValidRecord() {
        StationLogTrace rec = StationLogTrace.start(UUID.randomUUID(), "op", null);

        rec.markSuccess("OUT");

        assertThat(rec.getStatus()).isEqualTo(StationLogStatus.SUCCEEDED);
        assertThat(rec.getOutput()).isEqualTo("OUT");
    }

    @Test
    void finishError_shouldStoreThrowable() {
        StationLogTrace rec = StationLogTrace.start(UUID.randomUUID(), "op", null);

        Exception e = new RuntimeException("boom");
        rec.markFailed(e);

        assertThat(rec.getStatus()).isEqualTo(StationLogStatus.FAILED);
        assertThat(rec.getThrowables()).containsExactly(e);
    }

    @Test
    void withThrowable_shouldAccumulateErrors() {
        StationLogTrace rec = StationLogTrace.start(UUID.randomUUID(), "op", null);

        rec.addErrorHandlerException(new RuntimeException("A"));
        rec.addErrorHandlerException(new RuntimeException("B"));

        assertThat(rec.getThrowables()).hasSize(2);
    }
}
