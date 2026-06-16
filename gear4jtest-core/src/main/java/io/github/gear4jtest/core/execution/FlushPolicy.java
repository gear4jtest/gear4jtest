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
        BY_COUNT
    }
}
