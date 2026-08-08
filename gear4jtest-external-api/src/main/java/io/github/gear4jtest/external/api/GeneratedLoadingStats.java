package io.github.gear4jtest.external.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Point-in-time counters for generated assembly-line loading.
 *
 * @param cacheHits                  requests served by the classloader registry
 * @param cacheMisses                requests requiring a new load or a
 *                                   single-flight join
 * @param singleFlightJoins          requests joining an existing load
 * @param startedLoads               distinct loads started by a worker
 * @param successfulLoads            loads registered before their deadline
 * @param failedLoads                loads completed by an operational failure
 * @param timedOutLoads              loads terminated by the end-to-end deadline
 * @param rejectedLoads              loads rejected by a saturated or closed
 *                                   executor
 * @param inFlightLoads              distinct loads not yet completed
 * @param activeLoads                worker tasks currently executing
 * @param queuedLoads                worker tasks waiting for execution
 * @param totalLoadDurationNanos     cumulative worker duration
 * @param maxLoadDurationNanos       longest worker duration
 * @param artifactReadDurationNanos  cumulative artifact lookup/read duration
 * @param translationDurationNanos   cumulative translator duration
 * @param compilationDurationNanos   cumulative compiler-call duration
 * @param instantiationDurationNanos cumulative class loading, construction and
 *                                   injection duration
 * @param artifactIntegrityFailures  artifact metadata, size or digest
 *                                   mismatches observed while loading
 * @param phaseStats                 finite per-phase attempts, failures and
 *                                   durations
 * @param shutdown                   whether the loading runtime is closed
 */
public record GeneratedLoadingStats(long cacheHits,
                                    long cacheMisses,
                                    long singleFlightJoins,
                                    long startedLoads,
                                    long successfulLoads,
                                    long failedLoads,
                                    long timedOutLoads,
                                    long rejectedLoads,
                                    int inFlightLoads,
                                    int activeLoads,
                                    int queuedLoads,
                                    long totalLoadDurationNanos,
                                    long maxLoadDurationNanos,
                                    long artifactReadDurationNanos,
                                    long translationDurationNanos,
                                    long compilationDurationNanos,
                                    long instantiationDurationNanos,
                                    long artifactIntegrityFailures,
                                    Map<GeneratedLoadingPhase, GeneratedLoadingPhaseStats> phaseStats,
                                    boolean shutdown) {
    /**
     * Compatibility constructor for snapshots created before per-phase counters
     * were exposed.
     */
    public GeneratedLoadingStats(long cacheHits,
                                 long cacheMisses,
                                 long singleFlightJoins,
                                 long startedLoads,
                                 long successfulLoads,
                                 long failedLoads,
                                 long timedOutLoads,
                                 long rejectedLoads,
                                 int inFlightLoads,
                                 int activeLoads,
                                 int queuedLoads,
                                 long totalLoadDurationNanos,
                                 long maxLoadDurationNanos,
                                 long artifactReadDurationNanos,
                                 long translationDurationNanos,
                                 long compilationDurationNanos,
                                 long instantiationDurationNanos,
                                 boolean shutdown) {
        this(cacheHits, cacheMisses, singleFlightJoins, startedLoads, successfulLoads, failedLoads,
                timedOutLoads, rejectedLoads, inFlightLoads, activeLoads, queuedLoads,
                totalLoadDurationNanos, maxLoadDurationNanos, artifactReadDurationNanos,
                translationDurationNanos, compilationDurationNanos, instantiationDurationNanos,
                0L, Map.of(), shutdown);
    }

    public GeneratedLoadingStats {
        Objects.requireNonNull(phaseStats, "phaseStats must not be null");
        EnumMap<GeneratedLoadingPhase, GeneratedLoadingPhaseStats> snapshot = new EnumMap<>(
                GeneratedLoadingPhase.class);
        for (GeneratedLoadingPhase phase : GeneratedLoadingPhase.values()) {
            snapshot.put(phase, phaseStats.getOrDefault(phase, GeneratedLoadingPhaseStats.EMPTY));
        }
        phaseStats = Map.copyOf(snapshot);
    }

    /** Returns counters for one of the finite generated-loading phases. */
    public GeneratedLoadingPhaseStats phase(GeneratedLoadingPhase phase) {
        return phaseStats.get(Objects.requireNonNull(phase, "phase must not be null"));
    }
}
