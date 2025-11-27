package io.github.gear4jtest.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PipelineExecutionTest {

    @Test
    void pipelineExecution_shouldStoreOperations_order_status() {
        UUID id = UUID.randomUUID();

        OperationExecutionRecord r1 =
                OperationExecutionRecord.start("exec", "op1", null);
        r1.markSuccess("A");

        OperationExecutionRecord r2 =
                OperationExecutionRecord.start("exec", "op2", null);
        r2.markSuccess("B");

        PipelineExecution pe = new PipelineExecution(id, "pipe", Map.of());
        pe.getOperations().add(r1);
        pe.getOperations().add(r2);

        assertThat(pe.getOperations()).hasSize(2);
        assertThat(pe.getOperations().get(0).getOperationId()).isEqualTo("op1");
    }
}
