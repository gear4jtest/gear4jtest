package io.github.gear4jtest.jdbc.persistence;

import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcStatementOptionsTest {
    @Test
    void defaults_shouldApplyThirtySecondQueryTimeout() throws Exception {
        // Given
        Statement statement = mock(Statement.class);

        // When
        JdbcStatementOptions options = JdbcStatementOptions.defaults();
        options.apply(statement);

        // Then
        assertThat(options.queryTimeoutSeconds()).isEqualTo(30);
        verify(statement).setQueryTimeout(30);
    }

    @Test
    void of_shouldRoundSubSecondTimeoutUpToOneSecond() {
        // When
        JdbcStatementOptions options = JdbcStatementOptions.of(Duration.ofMillis(1));

        // Then
        assertThat(options.queryTimeoutSeconds()).isEqualTo(1);
    }

    @Test
    void of_shouldRejectNegativeTimeout() {
        Duration invalidTimeout = Duration.ofMillis(-1);

        // When / Then
        assertThatThrownBy(() -> JdbcStatementOptions.of(invalidTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queryTimeout");
    }
}
