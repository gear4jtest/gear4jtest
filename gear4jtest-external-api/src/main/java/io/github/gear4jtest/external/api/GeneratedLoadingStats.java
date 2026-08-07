package io.github.gear4jtest.external.api;

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
                                    boolean shutdown) {}
