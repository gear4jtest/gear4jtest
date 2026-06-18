package io.github.gear4jtest.spring.boot;

import java.time.Duration;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated Spring Boot properties for Gear4J. */
@Validated
@ConfigurationProperties(prefix = "gear4j")
public class Gear4jProperties {
    @Valid private final ParallelProperties parallel = new ParallelProperties();
    @Valid private final PersistenceProperties persistence = new PersistenceProperties();
    @Valid private final MetricsProperties metrics = new MetricsProperties();

    public ParallelProperties getParallel() {
        return parallel;
    }

    public PersistenceProperties getPersistence() {
        return persistence;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public static final class ParallelProperties {
        @NotNull private Duration defaultAwaitTimeout = Duration.ofSeconds(30);

        public Duration getDefaultAwaitTimeout() {
            return defaultAwaitTimeout;
        }

        public void setDefaultAwaitTimeout(Duration defaultAwaitTimeout) {
            this.defaultAwaitTimeout = defaultAwaitTimeout;
        }
    }

    public enum RedactionMode {
        /**
         * Persist as-is when no SensitiveDataRedactor bean is available, with a startup
         * warning.
         */
        WARN,
        /**
         * Fail startup when persistence is enabled and no SensitiveDataRedactor bean is
         * available.
         */
        REQUIRE,
        /** Explicitly allow persistence without redaction. */
        DISABLED
    }

    public static final class PersistenceProperties {
        /** Enables JDBC persistence for run and station traces. Default: false. */
        private boolean enabled;
        /** Required when JDBC persistence is enabled. */
        private Gear4jDatabaseDialect dialect;
        /**
         * Lets Gear4J create and migrate its internal schema automatically. Default:
         * false.
         */
        private boolean autoCreateTables;
        @Min(1) private int batchSize = 500;
        @Min(1) private int maxPendingLogsPerRun = 10_000;
        /**
         * Number of worker threads used for asynchronous JDBC station-log flushes.
         * Default: 1.
         */
        @Min(1) private int flushThreads = 1;
        /**
         * Maximum queued asynchronous JDBC flush tasks before appends fail fast.
         * Default: 1000.
         */
        @Min(1) private int maxScheduledFlushTasks = 1_000;
        @NotNull private Duration flushInterval = Duration.ofSeconds(1);
        @NotNull private Duration shutdownTimeout = Duration.ofSeconds(30);
        /**
         * JDBC Statement query timeout applied to Gear4J persistence statements. Use 0
         * to disable the statement-level timeout. Default: 30s.
         */
        @NotNull private Duration jdbcStatementTimeout = Duration.ofSeconds(30);
        /**
         * Controls startup behavior when persistence is enabled without a
         * SensitiveDataRedactor bean. Default: WARN.
         */
        @NotNull private RedactionMode redactionMode = RedactionMode.WARN;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Gear4jDatabaseDialect getDialect() {
            return dialect;
        }

        public void setDialect(Gear4jDatabaseDialect dialect) {
            this.dialect = dialect;
        }

        public boolean isAutoCreateTables() {
            return autoCreateTables;
        }

        public void setAutoCreateTables(boolean autoCreateTables) {
            this.autoCreateTables = autoCreateTables;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxPendingLogsPerRun() {
            return maxPendingLogsPerRun;
        }

        public void setMaxPendingLogsPerRun(int maxPendingLogsPerRun) {
            this.maxPendingLogsPerRun = maxPendingLogsPerRun;
        }

        public int getFlushThreads() {
            return flushThreads;
        }

        public void setFlushThreads(int flushThreads) {
            this.flushThreads = flushThreads;
        }

        public int getMaxScheduledFlushTasks() {
            return maxScheduledFlushTasks;
        }

        public void setMaxScheduledFlushTasks(int maxScheduledFlushTasks) {
            this.maxScheduledFlushTasks = maxScheduledFlushTasks;
        }

        public Duration getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }

        public Duration getJdbcStatementTimeout() {
            return jdbcStatementTimeout;
        }

        public void setJdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.jdbcStatementTimeout = jdbcStatementTimeout;
        }

        public RedactionMode getRedactionMode() {
            return redactionMode;
        }

        public void setRedactionMode(RedactionMode redactionMode) {
            this.redactionMode = redactionMode;
        }

        public void validateWhenEnabled() {
            if (!enabled) {
                return;
            }
            if (dialect == null) {
                throw new IllegalStateException("gear4j.persistence.dialect is required when persistence is enabled");
            }
            if (maxPendingLogsPerRun < batchSize) {
                throw new IllegalStateException("gear4j.persistence.max-pending-logs-per-run must be >= batch-size");
            }
            if (jdbcStatementTimeout != null && jdbcStatementTimeout.isNegative()) {
                throw new IllegalStateException("gear4j.persistence.jdbc-statement-timeout must be >= 0");
            }
        }
    }

    public static final class MetricsProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
