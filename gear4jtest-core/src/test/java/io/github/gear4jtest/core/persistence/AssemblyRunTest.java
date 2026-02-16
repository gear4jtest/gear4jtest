package io.github.gear4jtest.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AssemblyRunTest {

    @Test
    void pipelineExecution_shouldStoreOperations_order_status() {
        UUID id = UUID.randomUUID();

        StationLog r1 =
                StationLog.start(UUID.randomUUID(), "op1", null);
        r1.markSuccess("A");

        StationLog r2 =
                StationLog.start(UUID.randomUUID(), "op2", null);
        r2.markSuccess("B");

        AssemblyRun pe = new AssemblyRun(id, "pipe", Map.of());
        pe.getOperations().add(r1);
        pe.getOperations().add(r2);

        assertThat(pe.getOperations()).hasSize(2);
        assertThat(pe.getOperations().get(0).getOperationId()).isEqualTo("op1");
    }
}
