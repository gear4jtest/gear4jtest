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
 * @param maxGeneratedSourceBytes   hard UTF-8 source-size limit applied before
 *                                  dispatching a compilation
 * @param maxCompilationOutputBytes hard cumulative bytecode-size limit for one
 *                                  compiler result
 */
public record GeneratedCompilationConfiguration(Duration timeout,
                                                int maxConcurrentCompilations,
                                                int queueCapacity,
                                                long maxGeneratedSourceBytes,
                                                long maxCompilationOutputBytes) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_CONCURRENT_COMPILATIONS = 1;
    public static final int DEFAULT_QUEUE_CAPACITY = 32;
    public static final long DEFAULT_MAX_GENERATED_SOURCE_BYTES = 4L * 1024L * 1024L;
    public static final long DEFAULT_MAX_COMPILATION_OUTPUT_BYTES = 8L * 1024L * 1024L;

    /**
     * Backward-compatible constructor using the default hard source and bytecode
     * limits.
     */
    public GeneratedCompilationConfiguration(Duration timeout,
                                             int maxConcurrentCompilations,
                                             int queueCapacity) {
        this(timeout, maxConcurrentCompilations, queueCapacity, DEFAULT_MAX_GENERATED_SOURCE_BYTES,
                DEFAULT_MAX_COMPILATION_OUTPUT_BYTES);
    }

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
        if (maxGeneratedSourceBytes <= 0L) {
            throw new IllegalArgumentException("maxGeneratedSourceBytes must be > 0");
        }
        if (maxCompilationOutputBytes <= 0L) {
            throw new IllegalArgumentException("maxCompilationOutputBytes must be > 0");
        }
    }

    public static GeneratedCompilationConfiguration defaults() {
        return new GeneratedCompilationConfiguration(DEFAULT_TIMEOUT,
                DEFAULT_MAX_CONCURRENT_COMPILATIONS, DEFAULT_QUEUE_CAPACITY,
                DEFAULT_MAX_GENERATED_SOURCE_BYTES, DEFAULT_MAX_COMPILATION_OUTPUT_BYTES);
    }

    public GeneratedCompilationConfiguration withTimeout(Duration value) {
        return new GeneratedCompilationConfiguration(value, maxConcurrentCompilations, queueCapacity,
                maxGeneratedSourceBytes, maxCompilationOutputBytes);
    }

    public GeneratedCompilationConfiguration withMaxConcurrentCompilations(int value) {
        return new GeneratedCompilationConfiguration(timeout, value, queueCapacity, maxGeneratedSourceBytes,
                maxCompilationOutputBytes);
    }

    public GeneratedCompilationConfiguration withQueueCapacity(int value) {
        return new GeneratedCompilationConfiguration(timeout, maxConcurrentCompilations, value,
                maxGeneratedSourceBytes, maxCompilationOutputBytes);
    }

    public GeneratedCompilationConfiguration withMaxGeneratedSourceBytes(long value) {
        return new GeneratedCompilationConfiguration(timeout, maxConcurrentCompilations, queueCapacity, value,
                maxCompilationOutputBytes);
    }

    public GeneratedCompilationConfiguration withMaxCompilationOutputBytes(long value) {
        return new GeneratedCompilationConfiguration(timeout, maxConcurrentCompilations, queueCapacity,
                maxGeneratedSourceBytes, value);
    }
}
