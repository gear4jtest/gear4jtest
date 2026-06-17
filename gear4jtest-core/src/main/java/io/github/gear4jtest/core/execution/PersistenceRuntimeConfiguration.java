package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.Objects;

/** Bounded buffering and flush cadence used by JDBC execution persistence. */
public final class PersistenceRuntimeConfiguration {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_MAX_PENDING_LOGS_PER_RUN = 10_000;
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_FLUSH_THREAD_COUNT = 1;
    private static final int DEFAULT_MAX_SCHEDULED_FLUSH_TASKS = 1_000;

    private final int batchSize;
    private final int maxPendingLogsPerRun;
    private final Duration flushInterval;
    private final Duration shutdownTimeout;
    private final int flushThreadCount;
    private final int maxScheduledFlushTasks;

    private PersistenceRuntimeConfiguration(Builder builder) {
        this.batchSize = positive(builder.batchSize, "batchSize");
        this.maxPendingLogsPerRun = positive(builder.maxPendingLogsPerRun, "maxPendingLogsPerRun");
        if (maxPendingLogsPerRun < batchSize) {
            throw new IllegalArgumentException("maxPendingLogsPerRun must be >= batchSize");
        }
        this.flushInterval = positive(builder.flushInterval, "flushInterval");
        this.shutdownTimeout = positive(builder.shutdownTimeout, "shutdownTimeout");
        this.flushThreadCount = positive(builder.flushThreadCount, "flushThreadCount");
        this.maxScheduledFlushTasks = positive(builder.maxScheduledFlushTasks, "maxScheduledFlushTasks");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PersistenceRuntimeConfiguration defaults() {
        return builder().build();
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

    public int flushThreadCount() {
        return flushThreadCount;
    }

    public int maxScheduledFlushTasks() {
        return maxScheduledFlushTasks;
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

    public static final class Builder {
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int maxPendingLogsPerRun = DEFAULT_MAX_PENDING_LOGS_PER_RUN;
        private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
        private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private int flushThreadCount = DEFAULT_FLUSH_THREAD_COUNT;
        private int maxScheduledFlushTasks = DEFAULT_MAX_SCHEDULED_FLUSH_TASKS;

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

        public Builder flushThreadCount(int flushThreadCount) {
            this.flushThreadCount = flushThreadCount;
            return this;
        }

        public Builder maxScheduledFlushTasks(int maxScheduledFlushTasks) {
            this.maxScheduledFlushTasks = maxScheduledFlushTasks;
            return this;
        }

        public PersistenceRuntimeConfiguration build() {
            return new PersistenceRuntimeConfiguration(this);
        }
    }
}
