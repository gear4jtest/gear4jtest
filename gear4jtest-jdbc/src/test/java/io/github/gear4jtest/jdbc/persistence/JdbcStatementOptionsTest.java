package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcStatementOptionsTest {
    @Test
    void prepare_shouldCloseOnConfigurationFailureAndPreserveCleanupFailure() throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        var original = new SQLException("timeout configuration failed");
        var cleanup = new SQLException("close failed");
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        doThrow(original).when(statement).setQueryTimeout(30);
        doThrow(cleanup).when(statement).close();

        // When / Then
        assertThatThrownBy(() -> JdbcStatementOptions.defaults().prepare(connection, "SELECT 1"))
                .isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(cleanup);
        verify(statement).close();
        verify(connection, never()).close();
    }

    @Test
    void configureNewStatement_shouldCloseOnFatalConfigurationFailure() throws Exception {
        // Given
        Statement statement = mock(Statement.class);
        var fatal = new AssertionError("driver failed");
        doThrow(fatal).when(statement).setQueryTimeout(30);

        // When / Then
        assertThatThrownBy(() -> JdbcStatementOptions.defaults().configureNewStatement(statement)).isSameAs(fatal);
        verify(statement).close();
    }

    @Test
    void prepare_shouldLeaveSuccessfulStatementOpenForTheCaller() throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);

        // When / Then
        assertThat(JdbcStatementOptions.defaults().prepare(connection, "SELECT 1")).isSameAs(statement);
        verify(statement).setQueryTimeout(30);
        verify(statement, never()).close();
    }

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
    void of_shouldAcceptTheLargestJdbcTimeout() {
        // When
        JdbcStatementOptions options = JdbcStatementOptions.of(Duration.ofSeconds(Integer.MAX_VALUE));

        // Then
        assertThat(options.queryTimeoutSeconds()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void of_shouldRejectAFractionAboveTheLargestJdbcTimeout() {
        Duration invalidTimeout = Duration.ofSeconds(Integer.MAX_VALUE, 1L);

        // When / Then
        assertThatThrownBy(() -> JdbcStatementOptions.of(invalidTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queryTimeout is too large for JDBC Statement#setQueryTimeout");
    }

    @Test
    void of_shouldRejectHugeDurationsWithoutLeakingArithmeticException() {
        Duration invalidTimeout = Duration.ofSeconds(Long.MAX_VALUE);

        // When / Then
        assertThatThrownBy(() -> JdbcStatementOptions.of(invalidTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queryTimeout is too large for JDBC Statement#setQueryTimeout");
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
