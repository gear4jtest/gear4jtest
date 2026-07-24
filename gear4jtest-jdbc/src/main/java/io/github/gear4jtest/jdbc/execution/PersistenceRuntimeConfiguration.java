package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.Objects;

/** Bounded buffering and flush cadence used by JDBC execution persistence. */
public final class PersistenceRuntimeConfiguration {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_MAX_PENDING_LOGS_PER_RUN = 10_000;
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_SHUTDOWN_RETRY_INITIAL_BACKOFF = Duration.ofMillis(100);
    private static final Duration DEFAULT_SHUTDOWN_RETRY_MAX_BACKOFF = Duration.ofSeconds(2);
    private static final int DEFAULT_FLUSH_THREAD_COUNT = 1;
    private static final int DEFAULT_MAX_SCHEDULED_FLUSH_TASKS = 1_000;
    private static final Duration DEFAULT_JDBC_STATEMENT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_READINESS_MAX_BUFFERED_STATION_LOGS = 5_000;
    private static final Duration DEFAULT_READINESS_MAX_BACKLOG_AGE = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CONNECTIVITY_PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final int batchSize;
    private final int maxPendingLogsPerRun;
    private final Duration flushInterval;
    private final Duration shutdownTimeout;
    private final Duration shutdownRetryInitialBackoff;
    private final Duration shutdownRetryMaxBackoff;
    private final int flushThreadCount;
    private final int maxScheduledFlushTasks;
    private final Duration jdbcStatementTimeout;
    private final int readinessMaxBufferedStationLogs;
    private final Duration readinessMaxBacklogAge;
    private final Duration connectivityProbeTimeout;

    private PersistenceRuntimeConfiguration(Builder builder) {
        this.batchSize = positive(builder.batchSize, "batchSize");
        this.maxPendingLogsPerRun = positive(builder.maxPendingLogsPerRun, "maxPendingLogsPerRun");
        if (maxPendingLogsPerRun < batchSize) {
            throw new IllegalArgumentException("maxPendingLogsPerRun must be >= batchSize");
        }
        this.flushInterval = positive(builder.flushInterval, "flushInterval");
        this.shutdownTimeout = positive(builder.shutdownTimeout, "shutdownTimeout");
        this.shutdownRetryInitialBackoff = positive(builder.shutdownRetryInitialBackoff,
                                                    "shutdownRetryInitialBackoff");
        this.shutdownRetryMaxBackoff = positive(builder.shutdownRetryMaxBackoff, "shutdownRetryMaxBackoff");
        if (shutdownRetryInitialBackoff.compareTo(shutdownRetryMaxBackoff) > 0) {
            throw new IllegalArgumentException("shutdownRetryInitialBackoff must be <= shutdownRetryMaxBackoff");
        }
        this.flushThreadCount = positive(builder.flushThreadCount, "flushThreadCount");
        this.maxScheduledFlushTasks = positive(builder.maxScheduledFlushTasks, "maxScheduledFlushTasks");
        this.jdbcStatementTimeout = nonNegative(builder.jdbcStatementTimeout, "jdbcStatementTimeout");
        this.readinessMaxBufferedStationLogs = positive(builder.readinessMaxBufferedStationLogs,
                                                        "readinessMaxBufferedStationLogs");
        this.readinessMaxBacklogAge = positive(builder.readinessMaxBacklogAge, "readinessMaxBacklogAge");
        this.connectivityProbeTimeout = positive(builder.connectivityProbeTimeout, "connectivityProbeTimeout");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PersistenceRuntimeConfiguration defaults() {
        return builder().build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public int batchSize() {
        return batchSize;
    }

    public int maxPendingLogsPerRun() {
        return maxPendingLogsPerRun;
    }

    public Duration flushInterval() {
        return flushInterval;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    public Duration shutdownRetryInitialBackoff() {
        return shutdownRetryInitialBackoff;
    }

    public Duration shutdownRetryMaxBackoff() {
        return shutdownRetryMaxBackoff;
    }

    public int flushThreadCount() {
        return flushThreadCount;
    }

    public int maxScheduledFlushTasks() {
        return maxScheduledFlushTasks;
    }

    public Duration jdbcStatementTimeout() {
        return jdbcStatementTimeout;
    }

    public int readinessMaxBufferedStationLogs() {
        return readinessMaxBufferedStationLogs;
    }

    public Duration readinessMaxBacklogAge() {
        return readinessMaxBacklogAge;
    }

    public Duration connectivityProbeTimeout() {
        return connectivityProbeTimeout;
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    public static final class Builder {
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int maxPendingLogsPerRun = DEFAULT_MAX_PENDING_LOGS_PER_RUN;
        private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
        private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private Duration shutdownRetryInitialBackoff = DEFAULT_SHUTDOWN_RETRY_INITIAL_BACKOFF;
        private Duration shutdownRetryMaxBackoff = DEFAULT_SHUTDOWN_RETRY_MAX_BACKOFF;
        private int flushThreadCount = DEFAULT_FLUSH_THREAD_COUNT;
        private int maxScheduledFlushTasks = DEFAULT_MAX_SCHEDULED_FLUSH_TASKS;
        private Duration jdbcStatementTimeout = DEFAULT_JDBC_STATEMENT_TIMEOUT;
        private int readinessMaxBufferedStationLogs = DEFAULT_READINESS_MAX_BUFFERED_STATION_LOGS;
        private Duration readinessMaxBacklogAge = DEFAULT_READINESS_MAX_BACKLOG_AGE;
        private Duration connectivityProbeTimeout = DEFAULT_CONNECTIVITY_PROBE_TIMEOUT;

        private Builder() {
        }

        private Builder(PersistenceRuntimeConfiguration configuration) {
            this.batchSize = configuration.batchSize;
            this.maxPendingLogsPerRun = configuration.maxPendingLogsPerRun;
            this.flushInterval = configuration.flushInterval;
            this.shutdownTimeout = configuration.shutdownTimeout;
            this.shutdownRetryInitialBackoff = configuration.shutdownRetryInitialBackoff;
            this.shutdownRetryMaxBackoff = configuration.shutdownRetryMaxBackoff;
            this.flushThreadCount = configuration.flushThreadCount;
            this.maxScheduledFlushTasks = configuration.maxScheduledFlushTasks;
            this.jdbcStatementTimeout = configuration.jdbcStatementTimeout;
            this.readinessMaxBufferedStationLogs = configuration.readinessMaxBufferedStationLogs;
            this.readinessMaxBacklogAge = configuration.readinessMaxBacklogAge;
            this.connectivityProbeTimeout = configuration.connectivityProbeTimeout;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder maxPendingLogsPerRun(int maxPendingLogsPerRun) {
            this.maxPendingLogsPerRun = maxPendingLogsPerRun;
            return this;
        }

        public Builder flushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        public Builder shutdownRetryInitialBackoff(Duration shutdownRetryInitialBackoff) {
            this.shutdownRetryInitialBackoff = shutdownRetryInitialBackoff;
            return this;
        }

        public Builder shutdownRetryMaxBackoff(Duration shutdownRetryMaxBackoff) {
            this.shutdownRetryMaxBackoff = shutdownRetryMaxBackoff;
            return this;
        }

        public Builder flushThreadCount(int flushThreadCount) {
            this.flushThreadCount = flushThreadCount;
            return this;
        }

        public Builder maxScheduledFlushTasks(int maxScheduledFlushTasks) {
            this.maxScheduledFlushTasks = maxScheduledFlushTasks;
            return this;
        }

        public Builder jdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.jdbcStatementTimeout = jdbcStatementTimeout;
            return this;
        }

        public Builder readinessMaxBufferedStationLogs(int readinessMaxBufferedStationLogs) {
            this.readinessMaxBufferedStationLogs = readinessMaxBufferedStationLogs;
            return this;
        }

        public Builder readinessMaxBacklogAge(Duration readinessMaxBacklogAge) {
            this.readinessMaxBacklogAge = readinessMaxBacklogAge;
            return this;
        }

        public Builder connectivityProbeTimeout(Duration connectivityProbeTimeout) {
            this.connectivityProbeTimeout = connectivityProbeTimeout;
            return this;
        }

        public PersistenceRuntimeConfiguration build() {
            return new PersistenceRuntimeConfiguration(this);
        }
    }
}
