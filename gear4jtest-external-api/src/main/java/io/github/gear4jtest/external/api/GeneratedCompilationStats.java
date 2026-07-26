package io.github.gear4jtest.external.api;

/**
 * Point-in-time counters for generated-source compilation.
 *
 * <p>
 * Durations measure actual delegate execution. A timed-out delegate that
 * ignores interruption remains active until it returns, but its late result is
 * discarded and never enters the cache.
 * </p>
 *
 * @param cacheHits                     requests served by the completed cache
 * @param cacheMisses                   requests not found in the completed
 *                                      cache
 * @param singleFlightJoins             cache misses joined to an existing
 *                                      compilation
 * @param startedCompilations           delegate invocations that started
 * @param successfulCompilations        delegate results accepted before
 *                                      deadline
 * @param failedCompilations            delegate failures propagated to callers
 * @param timedOutCompilations          compilation flights terminated by
 *                                      deadline
 * @param rejectedCompilations          distinct compilations rejected because
 *                                      the bounded executor was saturated
 * @param limitRejectedCompilations     requests rejected by a hard source or
 *                                      bytecode limit
 * @param cachedEntries                 completed cache entries
 * @param cachedBytecodeBytes           bytecode retained by the completed cache
 * @param inFlightCompilations          distinct compilation keys not yet
 *                                      cleaned up
 * @param activeCompilations            delegate invocations currently executing
 * @param queuedCompilations            delegate invocations waiting for a
 *                                      worker
 * @param totalCompilationDurationNanos cumulative delegate execution duration
 * @param maxCompilationDurationNanos   longest observed delegate execution
 * @param shutdown                      whether the compilation runtime is
 *                                      closed
 */
public record GeneratedCompilationStats(long cacheHits,
                                        long cacheMisses,
                                        long singleFlightJoins,
                                        long startedCompilations,
                                        long successfulCompilations,
                                        long failedCompilations,
                                        long timedOutCompilations,
                                        long rejectedCompilations,
                                        long limitRejectedCompilations,
                                        int cachedEntries,
                                        long cachedBytecodeBytes,
                                        int inFlightCompilations,
                                        int activeCompilations,
                                        int queuedCompilations,
                                        long totalCompilationDurationNanos,
                                        long maxCompilationDurationNanos,
                                        boolean shutdown) {}
