package io.github.gear4jtest.external.api;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Mutable counters backing {@link GeneratedCompilationStats}. */
final class GeneratedCompilationCounters {
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder singleFlightJoins = new LongAdder();
    private final LongAdder startedCompilations = new LongAdder();
    private final LongAdder successfulCompilations = new LongAdder();
    private final LongAdder failedCompilations = new LongAdder();
    private final LongAdder timedOutCompilations = new LongAdder();
    private final LongAdder rejectedCompilations = new LongAdder();
    private final LongAdder limitRejectedCompilations = new LongAdder();
    private final AtomicLong totalCompilationDurationNanos = new AtomicLong();
    private final AtomicLong maxCompilationDurationNanos = new AtomicLong();

    void recordCacheHit() {
        cacheHits.increment();
    }

    void recordCacheMiss() {
        cacheMisses.increment();
    }

    void recordSingleFlightJoin() {
        singleFlightJoins.increment();
    }

    void recordCompilationStarted() {
        startedCompilations.increment();
    }

    void recordCompilationSucceeded() {
        successfulCompilations.increment();
    }

    void recordCompilationFailed() {
        failedCompilations.increment();
    }

    void recordCompilationTimedOut() {
        timedOutCompilations.increment();
    }

    void recordCompilationRejected() {
        rejectedCompilations.increment();
    }

    void recordCompilationLimitRejected() {
        limitRejectedCompilations.increment();
    }

    void recordDuration(long durationNanos) {
        long safeDuration = Math.max(0L, durationNanos);
        totalCompilationDurationNanos.getAndAccumulate(safeDuration,
                                                       GeneratedCompilationCounters::saturatingAdd);
        maxCompilationDurationNanos.accumulateAndGet(safeDuration, Math::max);
    }

    GeneratedCompilationStats snapshot(int cachedEntries,
                                       long cachedBytecodeBytes,
                                       int inFlightCompilations,
                                       int activeCompilations,
                                       int queuedCompilations,
                                       boolean shutdown) {
        return new GeneratedCompilationStats(cacheHits.sum(), cacheMisses.sum(), singleFlightJoins.sum(),
                startedCompilations.sum(), successfulCompilations.sum(), failedCompilations.sum(),
                timedOutCompilations.sum(), rejectedCompilations.sum(), limitRejectedCompilations.sum(),
                cachedEntries, cachedBytecodeBytes, inFlightCompilations, activeCompilations, queuedCompilations,
                totalCompilationDurationNanos.get(), maxCompilationDurationNanos.get(), shutdown);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
