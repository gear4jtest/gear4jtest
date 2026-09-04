package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable outcome of a bounded JDBC persistence shutdown. */
public record PersistenceShutdownReport(Instant startedAt,
                                        Duration elapsed,
                                        int initialActiveRuns,
                                        int initialBufferedStationLogs,
                                        int flushedStationLogs,
                                        int remainingActiveRuns,
                                        int remainingStationLogs,
                                        int flushAttempts,
                                        boolean deadlineReached,
                                        boolean interrupted,
                                        FlushExecutorShutdownStatus flushExecutorShutdownStatus,
                                        boolean shutdownJdbcExecutorTerminated,
                                        int droppedFlushTasks,
                                        List<RunFailure> failures) {

    private static final String UNFINISHED_OPERATION_ERROR_TYPE = "io.github.gear4jtest.jdbc.execution.UnfinishedPersistenceOperation";

    public PersistenceShutdownReport {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        Objects.requireNonNull(flushExecutorShutdownStatus, "flushExecutorShutdownStatus must not be null");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        requireNonNegative(initialActiveRuns, "initialActiveRuns");
        requireNonNegative(initialBufferedStationLogs, "initialBufferedStationLogs");
        requireNonNegative(flushedStationLogs, "flushedStationLogs");
        requireNonNegative(remainingActiveRuns, "remainingActiveRuns");
        requireNonNegative(remainingStationLogs, "remainingStationLogs");
        requireNonNegative(flushAttempts, "flushAttempts");
        requireNonNegative(droppedFlushTasks, "droppedFlushTasks");
    }

    public boolean successful() {
        return remainingActiveRuns == 0 && remainingStationLogs == 0 && !deadlineReached && !interrupted
                && flushExecutorShutdownStatus != FlushExecutorShutdownStatus.NOT_TERMINATED
                && shutdownJdbcExecutorTerminated && failures.isEmpty();
    }

    /**
     * Shutdown outcome for the regular asynchronous flush executor.
     */
    public enum FlushExecutorShutdownStatus {
        /** The executor belongs to the caller and was deliberately left running. */
        CALLER_OWNED,
        /**
         * The executor belongs to this component and terminated within the deadline.
         */
        TERMINATED,
        /**
         * The executor belongs to this component but did not terminate within the
         * deadline.
         */
        NOT_TERMINATED
    }

    /**
     * Number of normal operations admitted before shutdown that exceeded the
     * deadline.
     */
    public int unfinishedOperations() {
        return (int) failures.stream()
                .filter(failure -> UNFINISHED_OPERATION_ERROR_TYPE.equals(failure.errorType()))
                .count();
    }

    static RunFailure unfinishedOperation(UUID runId, int remainingStationLogs, List<UUID> affectedRunIds) {
        return new RunFailure(runId, 0, remainingStationLogs, UNFINISHED_OPERATION_ERROR_TYPE,
                "Persistence operation admitted before shutdown did not finish within the deadline. affectedRunIds="
                        + affectedRunIds);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    /**
     * Final diagnostic for one run whose logs or final state could not be
     * persisted.
     */
    public record RunFailure(UUID runId,
                             int attempts,
                             int remainingStationLogs,
                             String errorType,
                             String message) {
        public RunFailure {
            Objects.requireNonNull(runId, "runId must not be null");
            requireNonNegative(attempts, "attempts");
            requireNonNegative(remainingStationLogs, "remainingStationLogs");
            errorType = Objects.requireNonNullElse(errorType, "unknown");
            message = Objects.requireNonNullElse(message, "Persistence shutdown deadline reached");
        }
    }
}
