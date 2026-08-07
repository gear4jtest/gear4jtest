package io.github.gear4jtest.external.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Deadline and bounded-executor policy for generated assembly-line loading.
 *
 * <p>
 * The timeout covers artifact lookup and reading, translation, compilation,
 * class loading, construction and dependency injection. It is independent of
 * the compiler's own timeout so applications can reserve time for the other
 * phases of the load.
 * </p>
 *
 * @param timeout            end-to-end deadline including executor queue wait
 * @param maxConcurrentLoads maximum number of distinct loads running
 *                           concurrently
 * @param queueCapacity      maximum number of distinct loads waiting for a
 *                           worker
 */
public record GeneratedLoadingConfiguration(Duration timeout,
                                            int maxConcurrentLoads,
                                            int queueCapacity) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final int DEFAULT_MAX_CONCURRENT_LOADS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 32;

    public GeneratedLoadingConfiguration {
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        if (maxConcurrentLoads <= 0) {
            throw new IllegalArgumentException("maxConcurrentLoads must be > 0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
    }

    public static GeneratedLoadingConfiguration defaults() {
        return new GeneratedLoadingConfiguration(DEFAULT_TIMEOUT, DEFAULT_MAX_CONCURRENT_LOADS,
                DEFAULT_QUEUE_CAPACITY);
    }

    public GeneratedLoadingConfiguration withTimeout(Duration value) {
        return new GeneratedLoadingConfiguration(value, maxConcurrentLoads, queueCapacity);
    }

    public GeneratedLoadingConfiguration withMaxConcurrentLoads(int value) {
        return new GeneratedLoadingConfiguration(timeout, value, queueCapacity);
    }

    public GeneratedLoadingConfiguration withQueueCapacity(int value) {
        return new GeneratedLoadingConfiguration(timeout, maxConcurrentLoads, value);
    }
}
