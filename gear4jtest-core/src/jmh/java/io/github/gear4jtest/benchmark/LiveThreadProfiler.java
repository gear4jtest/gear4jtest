package io.github.gear4jtest.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Collection;
import java.util.List;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/** Captures the number of live JVM threads after each measured iteration. */
public final class LiveThreadProfiler implements InternalProfiler {
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    public LiveThreadProfiler() {
    }

    public LiveThreadProfiler(String initLine) {
        if (initLine != null && !initLine.isBlank()) {
            throw new IllegalArgumentException("LiveThreadProfiler does not accept options: " + initLine);
        }
    }

    @Override
    public String getDescription() {
        return "Live JVM thread count";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        // No setup is required.
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        return List.of(
                       new ScalarResult("threads.live", threadBean.getThreadCount(), "threads", AggregationPolicy.MAX),
                       new ScalarResult("heap.used.bytes", memoryBean.getHeapMemoryUsage().getUsed(), "bytes",
                               AggregationPolicy.MAX));
    }
}
