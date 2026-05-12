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
        return new FlushPolicy(Type.BY_COUNT, count, null, 0);
    }

    public static FlushPolicy byTime(Duration every) {
        return new FlushPolicy(Type.BY_TIME, 0, every, 0);
    }

    public static FlushPolicy byMemory(long approxBytes) {
        return new FlushPolicy(Type.BY_MEMORY, 0, null, approxBytes);
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
        BY_COUNT, BY_TIME, BY_MEMORY
    }
}
