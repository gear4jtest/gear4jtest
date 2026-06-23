package io.github.gear4jtest.spring.boot;

import java.time.Duration;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Gear4jPropertiesTest {
    @Test
    void properties_shouldExposeDocumentedDefaults() {
        // Given / When
        Gear4jProperties properties = new Gear4jProperties();

        // Then
        assertThat(properties.getParallel().getDefaultAwaitTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getMetrics().isEnabled()).isTrue();
        assertThat(properties.getPersistence().isEnabled()).isFalse();
        assertThat(properties.getPersistence().getBatchSize()).isEqualTo(500);
        assertThat(properties.getPersistence().getMaxPendingLogsPerRun()).isEqualTo(10_000);
        assertThat(properties.getPersistence().getFlushThreads()).isEqualTo(1);
        assertThat(properties.getPersistence().getMaxScheduledFlushTasks()).isEqualTo(1_000);
        assertThat(properties.getPersistence().getFlushInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getPersistence().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getPersistence().getJdbcStatementTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getPersistence().getRedactionMode()).isEqualTo(Gear4jProperties.RedactionMode.WARN);
    }

    @Test
    void setters_shouldUpdateNestedProperties() {
        // Given
        Gear4jProperties properties = new Gear4jProperties();

        // When
        properties.getParallel().setDefaultAwaitTimeout(Duration.ofSeconds(5));
        properties.getMetrics().setEnabled(false);
        properties.getPersistence().setEnabled(true);
        properties.getPersistence().setDialect(Gear4jDatabaseDialect.POSTGRESQL);
        properties.getPersistence().setAutoCreateTables(true);
        properties.getPersistence().setBatchSize(25);
        properties.getPersistence().setMaxPendingLogsPerRun(50);
        properties.getPersistence().setFlushThreads(3);
        properties.getPersistence().setMaxScheduledFlushTasks(12);
        properties.getPersistence().setFlushInterval(Duration.ofMillis(250));
        properties.getPersistence().setShutdownTimeout(Duration.ofSeconds(3));
        properties.getPersistence().setJdbcStatementTimeout(Duration.ZERO);
        properties.getPersistence().setRedactionMode(Gear4jProperties.RedactionMode.REQUIRE);

        // Then
        assertThat(properties.getParallel().getDefaultAwaitTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getMetrics().isEnabled()).isFalse();
        assertThat(properties.getPersistence().isEnabled()).isTrue();
        assertThat(properties.getPersistence().getDialect()).isEqualTo(Gear4jDatabaseDialect.POSTGRESQL);
        assertThat(properties.getPersistence().isAutoCreateTables()).isTrue();
        assertThat(properties.getPersistence().getBatchSize()).isEqualTo(25);
        assertThat(properties.getPersistence().getMaxPendingLogsPerRun()).isEqualTo(50);
        assertThat(properties.getPersistence().getFlushThreads()).isEqualTo(3);
        assertThat(properties.getPersistence().getMaxScheduledFlushTasks()).isEqualTo(12);
        assertThat(properties.getPersistence().getFlushInterval()).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.getPersistence().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getPersistence().getJdbcStatementTimeout()).isEqualTo(Duration.ZERO);
        assertThat(properties.getPersistence().getRedactionMode()).isEqualTo(Gear4jProperties.RedactionMode.REQUIRE);
    }

    @Test
    void validateWhenEnabled_shouldSkipValidationWhenPersistenceIsDisabled() {
        // Given
        Gear4jProperties.PersistenceProperties persistence = new Gear4jProperties.PersistenceProperties();
        persistence.setBatchSize(100);
        persistence.setMaxPendingLogsPerRun(1);
        persistence.setJdbcStatementTimeout(Duration.ofSeconds(-1));

        // When / Then
        assertThatCode(persistence::validateWhenEnabled).doesNotThrowAnyException();
    }

    @Test
    void validateWhenEnabled_shouldRejectInvalidEnabledPersistenceConfiguration() {
        // Given
        Gear4jProperties.PersistenceProperties missingDialect = new Gear4jProperties.PersistenceProperties();
        missingDialect.setEnabled(true);

        Gear4jProperties.PersistenceProperties tooSmallBuffer = validPersistence();
        tooSmallBuffer.setBatchSize(100);
        tooSmallBuffer.setMaxPendingLogsPerRun(99);

        Gear4jProperties.PersistenceProperties negativeStatementTimeout = validPersistence();
        negativeStatementTimeout.setJdbcStatementTimeout(Duration.ofMillis(-1));

        // When / Then
        assertThatThrownBy(missingDialect::validateWhenEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gear4j.persistence.dialect is required when persistence is enabled");
        assertThatThrownBy(tooSmallBuffer::validateWhenEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gear4j.persistence.max-pending-logs-per-run must be >= batch-size");
        assertThatThrownBy(negativeStatementTimeout::validateWhenEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gear4j.persistence.jdbc-statement-timeout must be >= 0");
        assertThatCode(validPersistence()::validateWhenEnabled).doesNotThrowAnyException();
    }

    private static Gear4jProperties.PersistenceProperties validPersistence() {
        Gear4jProperties.PersistenceProperties persistence = new Gear4jProperties.PersistenceProperties();
        persistence.setEnabled(true);
        persistence.setDialect(Gear4jDatabaseDialect.H2);
        return persistence;
    }
}
