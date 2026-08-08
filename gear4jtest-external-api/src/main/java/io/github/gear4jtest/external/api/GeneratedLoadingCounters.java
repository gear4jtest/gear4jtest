package io.github.gear4jtest.external.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import io.github.gear4jtest.external.api.artifact.ArtifactIntegrityException;

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
    private final LongAdder artifactIntegrityFailures = new LongAdder();
    private final Map<GeneratedLoadingPhase, PhaseCounters> phaseCounters = new EnumMap<>(GeneratedLoadingPhase.class);

    GeneratedLoadingCounters() {
        for (GeneratedLoadingPhase phase : GeneratedLoadingPhase.values()) {
            phaseCounters.put(phase, new PhaseCounters());
        }
    }

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

    void recordPhaseStarted(GeneratedLoadingPhase phase) {
        phaseCounters.get(phase).attempts.increment();
    }

    void recordPhaseFinished(GeneratedLoadingPhase phase, long durationNanos, Throwable failure) {
        PhaseCounters phaseCounter = phaseCounters.get(phase);
        phaseCounter.recordDuration(durationNanos);
        switch (phase) {
            case ARTIFACT_READ -> recordArtifactReadDuration(durationNanos);
            case TRANSLATION -> recordTranslationDuration(durationNanos);
            case COMPILATION -> recordCompilationDuration(durationNanos);
            case CLASS_LOADING, CONSTRUCTION, INJECTION -> recordInstantiationDuration(durationNanos);
        }
        if (failure != null) {
            phaseCounter.failures.increment();
            if (containsArtifactIntegrityFailure(failure)) {
                artifactIntegrityFailures.increment();
            }
        }
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
                instantiationDurationNanos.get(), artifactIntegrityFailures.sum(), snapshotPhases(), shutdown);
    }

    private Map<GeneratedLoadingPhase, GeneratedLoadingPhaseStats> snapshotPhases() {
        EnumMap<GeneratedLoadingPhase, GeneratedLoadingPhaseStats> snapshot = new EnumMap<>(
                GeneratedLoadingPhase.class);
        phaseCounters.forEach((phase, counters) -> snapshot.put(phase, counters.snapshot()));
        return snapshot;
    }

    private static boolean containsArtifactIntegrityFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ArtifactIntegrityException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private static final class PhaseCounters {
        private final LongAdder attempts = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final AtomicLong totalDurationNanos = new AtomicLong();
        private final AtomicLong maxDurationNanos = new AtomicLong();

        private void recordDuration(long durationNanos) {
            long safeDuration = Math.max(durationNanos, 0L);
            totalDurationNanos.getAndAccumulate(safeDuration, GeneratedLoadingCounters::saturatingAdd);
            maxDurationNanos.accumulateAndGet(safeDuration, Math::max);
        }

        private GeneratedLoadingPhaseStats snapshot() {
            return new GeneratedLoadingPhaseStats(attempts.sum(), failures.sum(), totalDurationNanos.get(),
                    maxDurationNanos.get());
        }
    }
}
