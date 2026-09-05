package io.github.gear4jtest.core.api;

import java.lang.reflect.Modifier;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionResultTest {
    @Test
    void constructors_shouldRemainPrivateSoOnlyValidatedFactoriesCanCreateResults() {
        assertThat(ExecutionResult.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void success_shouldExposeCompletedOutcome() {
        // Given / When
        ExecutionResult<String> executionResult = ExecutionResult.success("ok", null);

        // Then
        assertThat(executionResult.getResult()).isEqualTo("ok");
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
        assertThat(executionResult.isSuccess()).isTrue();
        assertThat(executionResult.isSkipped()).isFalse();
        assertThat(executionResult.isStopped()).isFalse();
        assertThat(executionResult.isCancelled()).isFalse();
        assertThat(executionResult.resultOptional()).contains("ok");
        assertThat(executionResult.executionOptional()).isEmpty();
    }

    @Test
    void skipped_shouldNotBeReportedAsSuccess() {
        // Given / When
        ExecutionResult<String> executionResult = ExecutionResult.skipped(null, null);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.SKIPPED);
        assertThat(executionResult.isSkipped()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.isFailed()).isFalse();
        assertThat(executionResult.resultOptional()).isEmpty();
        assertThat(executionResult.errorOptional()).isEmpty();
    }

    @Test
    void stopped_shouldNotBeReportedAsSuccess() {
        // Given / When
        ExecutionResult<String> executionResult = ExecutionResult.stopped("partial", null);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.STOPPED);
        assertThat(executionResult.isStopped()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
    }

    @Test
    void cancelled_shouldNotBeReportedAsFailureOrSuccess() {
        // Given
        RuntimeException cancellation = new RuntimeException("cancelled");

        // When
        ExecutionResult<Void> executionResult = ExecutionResult.cancelled(null, null, cancellation);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.CANCELLED);
        assertThat(executionResult.isCancelled()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.isFailed()).isFalse();
        assertThat(executionResult.getError()).isSameAs(cancellation);
        assertThat(executionResult.errorOptional()).contains(cancellation);
    }

    @Test
    void cancelled_shouldExposeAnEmptyOptionalWhenNoCauseIsAvailable() {
        ExecutionResult<Void> executionResult = ExecutionResult.cancelled(null, null, null);

        assertThat(executionResult.isCancelled()).isTrue();
        assertThat(executionResult.errorOptional()).isEmpty();
    }

    @Test
    void failure_shouldExposeFailedOutcome() {
        // Given
        RuntimeException error = new RuntimeException("boom");

        // When
        ExecutionResult<Void> executionResult = ExecutionResult.failure(error, null);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(executionResult.isFailed()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.getError()).isSameAs(error);
        assertThat(executionResult.resultOptional()).isEmpty();
        assertThat(executionResult.errorOptional()).contains(error);
    }

    @Test
    void failure_shouldRejectMissingError() {
        assertThatThrownBy(() -> ExecutionResult.failure(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("error must not be null");
    }

    @Test
    void publicNullableGetters_shouldExposeJSpecifyMetadata() throws Exception {
        assertThat(ExecutionResult.class.isAnnotationPresent(NullMarked.class)).isTrue();
        assertThat(ExecutionResult.class.getMethod("getResult").getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class)).isTrue();
        assertThat(ExecutionResult.class.getMethod("getExecution").getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class)).isTrue();
        assertThat(ExecutionResult.class.getMethod("getError").getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class)).isTrue();
    }
}
