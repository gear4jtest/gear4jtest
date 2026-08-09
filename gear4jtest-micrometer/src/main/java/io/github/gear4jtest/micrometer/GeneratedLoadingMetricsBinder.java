package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.external.api.AssemblyLineManager;
import io.github.gear4jtest.external.api.GeneratedLoadingPhase;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers bounded-cardinality metrics for generated loading and compilation.
 */
public final class GeneratedLoadingMetricsBinder {
    private GeneratedLoadingMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, AssemblyLineManager manager) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        bindLoading(meterRegistry, manager);
        bindCompilation(meterRegistry, manager);
    }

    private static void bindLoading(MeterRegistry meterRegistry, AssemblyLineManager manager) {
        registerCounter(meterRegistry, manager, "gear4j.generated.loading.cache.requests",
                        "Generated loading requests served by or missing the classloader registry",
                        value -> value.loadingStats().cacheHits(), "result", "hit");
        registerCounter(meterRegistry, manager, "gear4j.generated.loading.cache.requests",
                        "Generated loading requests served by or missing the classloader registry",
                        value -> value.loadingStats().cacheMisses(), "result", "miss");
        registerCounter(meterRegistry, manager, "gear4j.generated.loading.cache.requests",
                        "Generated loading requests served by or missing the classloader registry",
                        value -> value.loadingStats().singleFlightJoins(), "result", "single_flight_join");

        registerLoadingOutcome(meterRegistry, manager, "started", value -> value.loadingStats().startedLoads());
        registerLoadingOutcome(meterRegistry, manager, "succeeded", value -> value.loadingStats().successfulLoads());
        registerLoadingOutcome(meterRegistry, manager, "failed", value -> value.loadingStats().failedLoads());
        registerLoadingOutcome(meterRegistry, manager, "timeout", value -> value.loadingStats().timedOutLoads());
        registerLoadingOutcome(meterRegistry, manager, "rejected", value -> value.loadingStats().rejectedLoads());

        FunctionTimer.builder("gear4j.generated.loading.duration", manager,
                              value -> value.loadingStats().startedLoads(),
                              value -> value.loadingStats().totalLoadDurationNanos(), TimeUnit.NANOSECONDS)
                .description("Cumulative worker duration of generated assembly-line loads")
                .register(meterRegistry);
        registerGauge(meterRegistry, manager, "gear4j.generated.loading.duration.max.nanos",
                      "Maximum observed generated assembly-line load duration in nanoseconds",
                      value -> value.loadingStats().maxLoadDurationNanos());

        for (GeneratedLoadingPhase phase : GeneratedLoadingPhase.values()) {
            String[] phaseTag = { "phase", phase.metricTagValue() };
            FunctionTimer.builder("gear4j.generated.loading.phase.duration", manager,
                                  value -> value.loadingStats().phase(phase).attempts(),
                                  value -> value.loadingStats().phase(phase).totalDurationNanos(),
                                  TimeUnit.NANOSECONDS)
                    .description("Cumulative duration of one finite generated-loading phase")
                    .tags(phaseTag)
                    .register(meterRegistry);
            registerGauge(meterRegistry, manager, "gear4j.generated.loading.phase.duration.max.nanos",
                          "Maximum observed duration of one finite generated-loading phase in nanoseconds",
                          value -> value.loadingStats().phase(phase).maxDurationNanos(), phaseTag);
            registerCounter(meterRegistry, manager, "gear4j.generated.loading.phase.failures",
                            "Failures observed in one finite generated-loading phase",
                            value -> value.loadingStats().phase(phase).failures(), phaseTag);
        }

        registerCounter(meterRegistry, manager, "gear4j.generated.loading.artifact.integrity.failures",
                        "Artifact metadata, size or SHA-256 mismatches observed while loading",
                        value -> value.loadingStats().artifactIntegrityFailures());
        registerGauge(meterRegistry, manager, "gear4j.generated.loading.in.flight",
                      "Distinct generated-load slots not yet released, including late cleanup",
                      value -> value.loadingStats().inFlightLoads());
        registerGauge(meterRegistry, manager, "gear4j.generated.loading.executor.active",
                      "Generated-loading worker tasks currently executing",
                      value -> value.loadingStats().activeLoads());
        registerGauge(meterRegistry, manager, "gear4j.generated.loading.executor.queued",
                      "Generated-loading worker tasks waiting in the bounded queue",
                      value -> value.loadingStats().queuedLoads());
        registerGauge(meterRegistry, manager, "gear4j.generated.loading.shutdown",
                      "Whether the generated-loading runtime is shut down",
                      value -> value.loadingStats().shutdown() ? 1.0d : 0.0d);
    }

    private static void bindCompilation(MeterRegistry meterRegistry, AssemblyLineManager manager) {
        registerCounter(meterRegistry, manager, "gear4j.generated.compilation.cache.requests",
                        "Generated compilation requests served by or missing the bytecode cache",
                        value -> value.compilationStats().cacheHits(), "result", "hit");
        registerCounter(meterRegistry, manager, "gear4j.generated.compilation.cache.requests",
                        "Generated compilation requests served by or missing the bytecode cache",
                        value -> value.compilationStats().cacheMisses(), "result", "miss");
        registerCounter(meterRegistry, manager, "gear4j.generated.compilation.cache.requests",
                        "Generated compilation requests served by or missing the bytecode cache",
                        value -> value.compilationStats().singleFlightJoins(), "result", "single_flight_join");

        registerCompilationOutcome(meterRegistry, manager, "started",
                                   value -> value.compilationStats().startedCompilations());
        registerCompilationOutcome(meterRegistry, manager, "succeeded",
                                   value -> value.compilationStats().successfulCompilations());
        registerCompilationOutcome(meterRegistry, manager, "failed",
                                   value -> value.compilationStats().failedCompilations());
        registerCompilationOutcome(meterRegistry, manager, "timeout",
                                   value -> value.compilationStats().timedOutCompilations());
        registerCompilationOutcome(meterRegistry, manager, "rejected",
                                   value -> value.compilationStats().rejectedCompilations());
        registerCompilationOutcome(meterRegistry, manager, "limit_rejected",
                                   value -> value.compilationStats().limitRejectedCompilations());

        FunctionTimer.builder("gear4j.generated.compilation.duration", manager,
                              value -> value.compilationStats().startedCompilations(),
                              value -> value.compilationStats().totalCompilationDurationNanos(),
                              TimeUnit.NANOSECONDS)
                .description("Cumulative delegate duration of generated-source compilations")
                .register(meterRegistry);
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.duration.max.nanos",
                      "Maximum observed generated-source compilation duration in nanoseconds",
                      value -> value.compilationStats().maxCompilationDurationNanos());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.cache.entries",
                      "Generated bytecode entries retained by the completed compilation cache",
                      value -> value.compilationStats().cachedEntries());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.cache.bytes",
                      "Generated bytecode bytes retained by the completed compilation cache",
                      value -> value.compilationStats().cachedBytecodeBytes());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.in.flight",
                      "Distinct generated compilations that have not been cleaned up",
                      value -> value.compilationStats().inFlightCompilations());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.executor.active",
                      "Generated-source compiler delegates currently executing",
                      value -> value.compilationStats().activeCompilations());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.executor.queued",
                      "Generated-source compilations waiting in the bounded queue",
                      value -> value.compilationStats().queuedCompilations());
        registerGauge(meterRegistry, manager, "gear4j.generated.compilation.shutdown",
                      "Whether the generated-compilation runtime is shut down",
                      value -> value.compilationStats().shutdown() ? 1.0d : 0.0d);
    }

    private static void registerLoadingOutcome(MeterRegistry meterRegistry,
                                               AssemblyLineManager manager,
                                               String outcome,
                                               ToDoubleFunction<AssemblyLineManager> valueFunction) {
        registerCounter(meterRegistry, manager, "gear4j.generated.loading.loads",
                        "Generated assembly-line loads by finite terminal or lifecycle outcome",
                        valueFunction, "outcome", outcome);
    }

    private static void registerCompilationOutcome(MeterRegistry meterRegistry,
                                                   AssemblyLineManager manager,
                                                   String outcome,
                                                   ToDoubleFunction<AssemblyLineManager> valueFunction) {
        registerCounter(meterRegistry, manager, "gear4j.generated.compilations",
                        "Generated-source compilations by finite terminal or lifecycle outcome",
                        valueFunction, "outcome", outcome);
    }

    private static void registerCounter(MeterRegistry meterRegistry,
                                        AssemblyLineManager manager,
                                        String name,
                                        String description,
                                        ToDoubleFunction<AssemblyLineManager> valueFunction,
                                        String... tags) {
        FunctionCounter.builder(name, manager, valueFunction)
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }

    private static void registerGauge(MeterRegistry meterRegistry,
                                      AssemblyLineManager manager,
                                      String name,
                                      String description,
                                      ToDoubleFunction<AssemblyLineManager> valueFunction,
                                      String... tags) {
        Gauge.builder(name, manager, valueFunction)
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }
}
