package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.trace.RunTrace;

/**
 * Provider-neutral lifecycle contract used by the built-in persistence
 * extension.
 */
public interface RunPersistenceManager {
    void start(RunTrace execution);

    /**
     * Starts a run with its effective persistence configuration.
     *
     * <p>
     * Implementations that do not expose per-run tuning can keep implementing the
     * original {@link #start(RunTrace)} method. Buffering managers should override
     * this method and retain the supplied flush threshold in run-local state.
     * </p>
     */
    default void start(RunTrace execution, PersistenceConfiguration configuration) {
        start(execution);
    }

    default void append(StationLogRecord stationLogRecord) {
        // no-op by default
    }

    default void appendAll(List<StationLogRecord> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    default void heartbeat(UUID runId) {
        // no-op by default
    }

    default void flush(UUID runId) {
        // no-op by default
    }

    void end(RunTrace finalExecution);

    default void shutdown() {
        // no-op by default
    }
}
