package io.github.gear4jtest.external.api;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class GeneratedLoadingCounters {
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder singleFlightJoins = new LongAdder();
    private final LongAdder startedLoads = new LongAdder();
    private final LongAdder successfulLoads = new LongAdder();
    private final LongAdder failedLoads = new LongAdder();
    private final LongAdder timedOutLoads = new LongAdder();
    private final LongAdder rejectedLoads = new LongAdder();
    private final AtomicLong totalLoadDurationNanos = new AtomicLong();
    private final AtomicLong maxLoadDurationNanos = new AtomicLong();
    private final AtomicLong artifactReadDurationNanos = new AtomicLong();
    private final AtomicLong translationDurationNanos = new AtomicLong();
    private final AtomicLong compilationDurationNanos = new AtomicLong();
    private final AtomicLong instantiationDurationNanos = new AtomicLong();

    void recordCacheHit() {
        cacheHits.increment();
    }

    void recordCacheMiss() {
        cacheMisses.increment();
    }

    void recordSingleFlightJoin() {
        singleFlightJoins.increment();
    }

    void recordLoadStarted() {
        startedLoads.increment();
    }

    void recordLoadSucceeded() {
        successfulLoads.increment();
    }

    void recordLoadFailed() {
        failedLoads.increment();
    }

    void recordLoadTimedOut() {
        timedOutLoads.increment();
    }

    void recordLoadRejected() {
        rejectedLoads.increment();
    }

    void recordLoadDuration(long durationNanos) {
        long safeDuration = Math.max(durationNanos, 0L);
        totalLoadDurationNanos.getAndAccumulate(safeDuration, GeneratedLoadingCounters::saturatingAdd);
        maxLoadDurationNanos.accumulateAndGet(safeDuration, Math::max);
    }

    void recordArtifactReadDuration(long durationNanos) {
        recordDuration(artifactReadDurationNanos, durationNanos);
    }

    void recordTranslationDuration(long durationNanos) {
        recordDuration(translationDurationNanos, durationNanos);
    }

    void recordCompilationDuration(long durationNanos) {
        recordDuration(compilationDurationNanos, durationNanos);
    }

    void recordInstantiationDuration(long durationNanos) {
        recordDuration(instantiationDurationNanos, durationNanos);
    }

    GeneratedLoadingStats snapshot(int inFlightLoads,
                                   int activeLoads,
                                   int queuedLoads,
                                   boolean shutdown) {
        return new GeneratedLoadingStats(cacheHits.sum(), cacheMisses.sum(), singleFlightJoins.sum(),
                startedLoads.sum(), successfulLoads.sum(), failedLoads.sum(), timedOutLoads.sum(),
                rejectedLoads.sum(), inFlightLoads, activeLoads, queuedLoads,
                totalLoadDurationNanos.get(), maxLoadDurationNanos.get(), artifactReadDurationNanos.get(),
                translationDurationNanos.get(), compilationDurationNanos.get(),
                instantiationDurationNanos.get(), shutdown);
    }

    private static void recordDuration(AtomicLong counter, long durationNanos) {
        counter.getAndAccumulate(Math.max(durationNanos, 0L), GeneratedLoadingCounters::saturatingAdd);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
