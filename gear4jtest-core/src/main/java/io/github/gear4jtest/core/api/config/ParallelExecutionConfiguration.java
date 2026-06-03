package io.github.gear4jtest.core.api.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Engine-level safety defaults for asynchronous parallel container execution.
 */
public final class ParallelExecutionConfiguration {
    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(30);
    private final Duration defaultAwaitTimeout;

    private ParallelExecutionConfiguration(Duration defaultAwaitTimeout) {
        this.defaultAwaitTimeout = validate(defaultAwaitTimeout);
    }

    /**
     * Returns operational defaults used when a parallel station does not override
     * them.
     */
    public static ParallelExecutionConfiguration defaults() {
        return new ParallelExecutionConfiguration(DEFAULT_AWAIT_TIMEOUT);
    }

    /** Creates configuration with a mandatory positive default wait timeout. */
    public static ParallelExecutionConfiguration withDefaultAwaitTimeout(Duration timeout) {
        return new ParallelExecutionConfiguration(timeout);
    }

    public Duration defaultAwaitTimeout() {
        return defaultAwaitTimeout;
    }

    /** A station-level value wins; otherwise the engine default is applied. */
    public Duration effectiveAwaitTimeout(Duration stationAwaitTimeout) {
        return stationAwaitTimeout != null ? validate(stationAwaitTimeout) : defaultAwaitTimeout;
    }

    private static Duration validate(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        return timeout;
    }
}
