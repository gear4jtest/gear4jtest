package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExecutionResultTest {

    @Test
    void constructor_shouldStoreAllFieldsForSuccess() {
        UUID execId = UUID.randomUUID();
        String result = "ok";

        ExecutionResult<String> executionResult =
                new ExecutionResult<>(execId, result, true, null);

        assertThat(executionResult.getExecutionId()).isEqualTo(execId);
        assertThat(executionResult.getResult()).isEqualTo(result);
        assertThat(executionResult.isSuccess()).isTrue();
        assertThat(executionResult.getError()).isNull();
    }

    @Test
    void constructor_shouldStoreErrorForFailure() {
        UUID execId = UUID.randomUUID();
        RuntimeException error = new RuntimeException("boom");

        ExecutionResult<Void> executionResult =
                new ExecutionResult<>(execId, null, false, error);

        assertThat(executionResult.getExecutionId()).isEqualTo(execId);
        assertThat(executionResult.getResult()).isNull();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.getError()).isSameAs(error);
    }
}
