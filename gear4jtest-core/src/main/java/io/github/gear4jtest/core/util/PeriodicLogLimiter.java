package io.github.gear4jtest.core.util;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

import io.github.gear4jtest.core.api.annotation.Internal;

/**
 * Bounds repeated operational log signals while retaining the number omitted
 * between emissions.
 */
@Internal
public final class PeriodicLogLimiter {
    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private boolean emitted;
    private long nextEmissionNanos;
    private long suppressed;

    private PeriodicLogLimiter(Duration interval, LongSupplier nanoTime) {
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be greater than zero");
        }
        this.intervalNanos = interval.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public static PeriodicLogLimiter every(Duration interval) {
        return new PeriodicLogLimiter(interval, System::nanoTime);
    }

    static PeriodicLogLimiter every(Duration interval, LongSupplier nanoTime) {
        return new PeriodicLogLimiter(interval, nanoTime);
    }

    /**
     * Returns an emission permit for the first signal and then at most once per
     * configured monotonic interval.
     */
    public synchronized Emission acquire() {
        long now = nanoTime.getAsLong();
        if (!emitted || now - nextEmissionNanos >= 0L) {
            long suppressedSincePreviousEmission = suppressed;
            emitted = true;
            nextEmissionNanos = now + intervalNanos;
            suppressed = 0L;
            return new Emission(true, suppressedSincePreviousEmission);
        }
        if (suppressed < Long.MAX_VALUE) {
            suppressed++;
        }
        return Emission.SUPPRESSED;
    }

    /** Result of admitting or suppressing one repeated log signal. */
    public record Emission(boolean permitted, long suppressedSincePreviousEmission) {
        private static final Emission SUPPRESSED = new Emission(false, 0L);
    }
}
