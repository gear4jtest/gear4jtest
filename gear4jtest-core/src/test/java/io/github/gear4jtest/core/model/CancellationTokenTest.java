package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.exception.PipelineCancellationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationTokenTest {
    @Test
    void cancel_shouldPreserveFirstCancellationReason() {
        // Given
        CancellationToken token = new CancellationToken();

        // When
        boolean first = token.cancel("first");
        boolean second = token.cancel("second");

        // Then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(token.cancellationCause()).get().extracting(Throwable::getMessage).isEqualTo("first");
        assertThatThrownBy(token::throwIfCancellationRequested).isInstanceOf(PipelineCancellationException.class)
                .hasMessage("first");
    }
}
