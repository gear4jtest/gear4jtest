package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.api.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResultTest {

    @Test
    void constructor_shouldStoreAllFieldsForSuccess() {
        String result = "ok";

        ExecutionResult<String> executionResult =
                new ExecutionResult<>(result, true, null, null);

        assertThat(executionResult.getResult()).isEqualTo(result);
        assertThat(executionResult.isSuccess()).isTrue();
        assertThat(executionResult.getError()).isNull();
    }

    @Test
    void constructor_shouldStoreErrorForFailure() {
        RuntimeException error = new RuntimeException("boom");

        ExecutionResult<Void> executionResult =
                new ExecutionResult<>(null, false, null, error);

        assertThat(executionResult.getResult()).isNull();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.getError()).isSameAs(error);
    }
}
