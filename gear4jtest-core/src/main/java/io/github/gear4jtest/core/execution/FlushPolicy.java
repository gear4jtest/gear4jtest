package io.github.gear4jtest.core.execution;

import java.time.Duration;

public final class FlushPolicy {
    private final Type type;
    private final int count;
    private final Duration every;
    private final long approxBytes;

    private FlushPolicy(Type type, int count, Duration every, long approxBytes) {
        this.type = type;
        this.count = count;
        this.every = every;
        this.approxBytes = approxBytes;
    }

    public static FlushPolicy byCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        return new FlushPolicy(Type.BY_COUNT, count, null, 0);
    }

    /**
     * Time-based flushing is not implemented by {@link DatabaseExecutionManager}
     * yet. Use {@link #byCount(int)} for the current persistence runtime.
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static FlushPolicy byTime(Duration every) {
        throw new UnsupportedOperationException(
                "Time-based flushing is not implemented yet. Use FlushPolicy.byCount(int).");
    }

    /**
     * Memory-based flushing is not implemented by {@link DatabaseExecutionManager}
     * yet. Use {@link #byCount(int)} for the current persistence runtime.
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public static FlushPolicy byMemory(long approxBytes) {
        throw new UnsupportedOperationException(
                "Memory-based flushing is not implemented yet. Use FlushPolicy.byCount(int).");
    }

    public Type type() {
        return type;
    }

    public int count() {
        return count;
    }

    public Duration every() {
        return every;
    }

    public long approxBytes() {
        return approxBytes;
    }

    public enum Type {
        BY_COUNT,
        /**
         * Not implemented yet by {@link DatabaseExecutionManager}. Kept only to avoid
         * source-breaking callers that may already reference the enum constant.
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        BY_TIME,
        /**
         * Not implemented yet by {@link DatabaseExecutionManager}. Kept only to avoid
         * source-breaking callers that may already reference the enum constant.
         */
        @Deprecated(since = "1.0.0", forRemoval = false)
        BY_MEMORY
    }
}
