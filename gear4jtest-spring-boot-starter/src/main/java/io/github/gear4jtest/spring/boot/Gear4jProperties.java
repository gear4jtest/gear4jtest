package io.github.gear4jtest.spring.boot;

import java.time.Duration;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
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
         * Use Gear4J's metadata-only policy when no {@code SensitiveDataRedactor} bean
         * is available. Contexts are replaced by empty maps and payloads, results and
         * error messages are discarded.
         */
        DISCARD,
        /**
         * Persist as-is when no {@code SensitiveDataRedactor} bean is available, with a
         * startup warning.
         *
         * @deprecated use {@link #DISCARD} for the safe default, {@link #REQUIRE} to
         *             fail fast or {@link #DISABLED} for an explicit raw-capture opt-in
         */
        @Deprecated(forRemoval = true)
        WARN,
        /**
         * Fail startup when persistence is enabled and no effective
         * {@code SensitiveDataRedactor} bean is available.
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
        /**
         * Explicitly allows a compatible existing schema without Gear4J migration
         * history to be baselined. Default: false.
         */
        private boolean baselineOnMigrate;
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
        @NotNull private Duration shutdownRetryInitialBackoff = Duration.ofMillis(100);
        @NotNull private Duration shutdownRetryMaxBackoff = Duration.ofSeconds(2);
        /**
         * JDBC Statement query timeout applied to Gear4J persistence statements. Use 0
         * to disable the statement-level timeout. Default: 30s.
         */
        @NotNull private Duration jdbcStatementTimeout = Duration.ofSeconds(30);
        @Min(1) private int readinessMaxBufferedStationLogs = 5_000;
        @NotNull private Duration readinessMaxBacklogAge = Duration.ofSeconds(30);
        @NotNull private Duration connectivityProbeTimeout = Duration.ofSeconds(2);
        /**
         * Controls sensitive-value handling when persistence is enabled without a
         * SensitiveDataRedactor bean. Default: DISCARD.
         */
        @NotNull private RedactionMode redactionMode = RedactionMode.DISCARD;

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

        public boolean isBaselineOnMigrate() {
            return baselineOnMigrate;
        }

        public void setBaselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
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

        public Duration getShutdownRetryInitialBackoff() {
            return shutdownRetryInitialBackoff;
        }

        public void setShutdownRetryInitialBackoff(Duration shutdownRetryInitialBackoff) {
            this.shutdownRetryInitialBackoff = shutdownRetryInitialBackoff;
        }

        public Duration getShutdownRetryMaxBackoff() {
            return shutdownRetryMaxBackoff;
        }

        public void setShutdownRetryMaxBackoff(Duration shutdownRetryMaxBackoff) {
            this.shutdownRetryMaxBackoff = shutdownRetryMaxBackoff;
        }

        public Duration getJdbcStatementTimeout() {
            return jdbcStatementTimeout;
        }

        public void setJdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.jdbcStatementTimeout = jdbcStatementTimeout;
        }

        public int getReadinessMaxBufferedStationLogs() {
            return readinessMaxBufferedStationLogs;
        }

        public void setReadinessMaxBufferedStationLogs(int readinessMaxBufferedStationLogs) {
            this.readinessMaxBufferedStationLogs = readinessMaxBufferedStationLogs;
        }

        public Duration getReadinessMaxBacklogAge() {
            return readinessMaxBacklogAge;
        }

        public void setReadinessMaxBacklogAge(Duration readinessMaxBacklogAge) {
            this.readinessMaxBacklogAge = readinessMaxBacklogAge;
        }

        public Duration getConnectivityProbeTimeout() {
            return connectivityProbeTimeout;
        }

        public void setConnectivityProbeTimeout(Duration connectivityProbeTimeout) {
            this.connectivityProbeTimeout = connectivityProbeTimeout;
        }

        public RedactionMode getRedactionMode() {
            return redactionMode;
        }

        public void setRedactionMode(RedactionMode redactionMode) {
            this.redactionMode = redactionMode;
        }

        private static void requirePositive(Duration value, String property) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalStateException(property + " must be > 0");
            }
        }

        public void validateWhenEnabled() {
            if (!enabled) {
                return;
            }
            if (dialect == null) {
                throw new IllegalStateException("gear4j.persistence.dialect is required when persistence is enabled");
            }
            if (baselineOnMigrate && !autoCreateTables) {
                throw new IllegalStateException(
                        "gear4j.persistence.baseline-on-migrate requires auto-create-tables=true");
            }
            if (maxPendingLogsPerRun < batchSize) {
                throw new IllegalStateException("gear4j.persistence.max-pending-logs-per-run must be >= batch-size");
            }
            if (readinessMaxBufferedStationLogs <= 0) {
                throw new IllegalStateException("gear4j.persistence.readiness-max-buffered-station-logs must be > 0");
            }
            if (jdbcStatementTimeout != null && jdbcStatementTimeout.isNegative()) {
                throw new IllegalStateException("gear4j.persistence.jdbc-statement-timeout must be >= 0");
            }
            requirePositive(readinessMaxBacklogAge, "gear4j.persistence.readiness-max-backlog-age");
            requirePositive(connectivityProbeTimeout, "gear4j.persistence.connectivity-probe-timeout");
            if (shutdownRetryInitialBackoff == null || shutdownRetryInitialBackoff.isZero()
                    || shutdownRetryInitialBackoff.isNegative()) {
                throw new IllegalStateException("gear4j.persistence.shutdown-retry-initial-backoff must be > 0");
            }
            if (shutdownRetryMaxBackoff == null || shutdownRetryMaxBackoff.isZero()
                    || shutdownRetryMaxBackoff.isNegative()) {
                throw new IllegalStateException("gear4j.persistence.shutdown-retry-max-backoff must be > 0");
            }
            if (shutdownRetryInitialBackoff != null && shutdownRetryMaxBackoff != null
                    && shutdownRetryInitialBackoff.compareTo(shutdownRetryMaxBackoff) > 0) {
                throw new IllegalStateException("gear4j.persistence.shutdown-retry-initial-backoff must be <= "
                        + "shutdown-retry-max-backoff");
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
