package io.github.gear4jtest.external.api.exception;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompilationExceptionTest {
    @Test
    void constructors_shouldPreserveMessageCauseAndDiagnostics() {
        // Given
        IllegalStateException cause = new IllegalStateException("compiler crashed");

        // When
        CompilationException messageOnly = new CompilationException("failed");
        CompilationException causeOnly = new CompilationException(cause);
        CompilationException nullCause = new CompilationException((Throwable) null);
        CompilationException withDiagnostics = new CompilationException("failed", List.of("line 1", "line 2"), cause);

        // Then
        assertThat(messageOnly).hasMessage("failed").hasNoCause();
        assertThat(messageOnly.diagnostics()).isEmpty();
        assertThat(causeOnly).hasMessage("compiler crashed").hasCause(cause);
        assertThat(nullCause).hasMessage("Compilation failed").hasNoCause();
        assertThat(withDiagnostics).hasMessage("failed" + System.lineSeparator() + "line 1"
                + System.lineSeparator() + "line 2").hasCause(cause);
        assertThat(withDiagnostics.diagnostics()).containsExactly("line 1", "line 2");
    }

    @Test
    void constructor_shouldDefensivelyHandleNullAndMutableDiagnostics() {
        // Given
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add("line 1");

        // When
        CompilationException exception = new CompilationException("failed", diagnostics);
        diagnostics.add("late mutation");
        CompilationException nullDiagnostics = new CompilationException("failed", null, null);

        // Then
        assertThat(exception.diagnostics()).containsExactly("line 1");
        assertThatThrownBy(() -> exception.diagnostics().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(nullDiagnostics).hasMessage("failed");
        assertThat(nullDiagnostics.diagnostics()).isEmpty();
    }

    @Test
    void timeoutException_shouldExposeClassNameAndDeadline() {
        // When
        CompilationTimeoutException exception = new CompilationTimeoutException("io.test.Generated",
                Duration.ofSeconds(2));

        // Then
        assertThat(exception)
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("PT2S")
                .hasMessageContaining("io.test.Generated");
        assertThat(exception.className()).isEqualTo("io.test.Generated");
        assertThat(exception.timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(exception.errorCode()).isEqualTo(ExternalErrorCode.COMPILATION);
    }
}
