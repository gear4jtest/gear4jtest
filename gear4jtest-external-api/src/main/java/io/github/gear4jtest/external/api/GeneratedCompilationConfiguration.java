package io.github.gear4jtest.external.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource and deadline policy for generated-source compilation.
 *
 * <p>
 * The default single worker intentionally serializes access to compiler
 * implementations that do not advertise thread safety. Applications may opt
 * into more parallelism when their compiler implementation supports it.
 * </p>
 *
 * @param timeout                   end-to-end deadline including executor queue
 *                                  wait
 * @param maxConcurrentCompilations maximum number of compiler invocations
 *                                  running concurrently
 * @param queueCapacity             maximum number of distinct compilations
 *                                  waiting for a worker
 */
public record GeneratedCompilationConfiguration(Duration timeout,
                                                int maxConcurrentCompilations,
                                                int queueCapacity) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_CONCURRENT_COMPILATIONS = 1;
    public static final int DEFAULT_QUEUE_CAPACITY = 32;

    public GeneratedCompilationConfiguration {
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
        if (maxConcurrentCompilations <= 0) {
            throw new IllegalArgumentException("maxConcurrentCompilations must be > 0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
    }

    public static GeneratedCompilationConfiguration defaults() {
        return new GeneratedCompilationConfiguration(DEFAULT_TIMEOUT,
                DEFAULT_MAX_CONCURRENT_COMPILATIONS, DEFAULT_QUEUE_CAPACITY);
    }

    public GeneratedCompilationConfiguration withTimeout(Duration value) {
        return new GeneratedCompilationConfiguration(value, maxConcurrentCompilations, queueCapacity);
    }

    public GeneratedCompilationConfiguration withMaxConcurrentCompilations(int value) {
        return new GeneratedCompilationConfiguration(timeout, value, queueCapacity);
    }

    public GeneratedCompilationConfiguration withQueueCapacity(int value) {
        return new GeneratedCompilationConfiguration(timeout, maxConcurrentCompilations, value);
    }
}
