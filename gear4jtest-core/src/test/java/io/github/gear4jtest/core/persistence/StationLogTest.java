package io.github.gear4jtest.core.persistence;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StationLogTest {

    @Test
    void start_finishSuccess_shouldProduceValidRecord() {
        StationLog rec = StationLog.start(UUID.randomUUID(), "op", null);

        rec.markSuccess("OUT");

        assertThat(rec.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
        assertThat(rec.<String>getOutput()).isEqualTo("OUT");
    }

    @Test
    void finishError_shouldStoreThrowable() {
        StationLog rec = StationLog.start(UUID.randomUUID(), "op", null);

        Exception e = new RuntimeException("boom");
        rec.markFailed(e);

        assertThat(rec.getStatus()).isEqualTo(StationLog.Status.FAILED);
        assertThat(rec.getThrowables()).containsExactly(e);
    }

    @Test
    void withThrowable_shouldAccumulateErrors() {
        StationLog rec = StationLog.start(UUID.randomUUID(), "op", null);

        rec.addErrorHandlerException(new RuntimeException("A"));
        rec.addErrorHandlerException(new RuntimeException("B"));

        assertThat(rec.getThrowables()).hasSize(2);
    }
}
