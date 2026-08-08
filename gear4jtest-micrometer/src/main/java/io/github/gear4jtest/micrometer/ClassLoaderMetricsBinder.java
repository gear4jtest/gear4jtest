package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers tag-free occupancy, eviction and rejection metrics for
 * classloaders.
 */
public final class ClassLoaderMetricsBinder {
    private ClassLoaderMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, InMemoryClassLoaderRegistry registry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.cached",
                      "Generated classloaders currently retained by the registry",
                      value -> value.snapshotStats().cachedLoaders());
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.capacity",
                      "Configured maximum number of unprotected generated classloaders",
                      value -> value.snapshotStats().maxLoaders());
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.protected",
                      "Generated classloaders currently protected by at least one alias",
                      InMemoryClassLoaderRegistry::protectedLoaderCount);
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.protected.capacity",
                      "Configured maximum number of alias-protected generated classloaders",
                      InMemoryClassLoaderRegistry::maxProtectedLoaders);
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.aliases",
                      "Mutable aliases currently registered for generated classloaders",
                      value -> value.snapshotStats().aliases());
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.bytecode.bytes",
                      "Conservative bytecode weight retained by generated classloaders",
                      value -> value.snapshotStats().bytecodeWeightBytes());
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.bytecode.capacity.bytes",
                      "Hard bytecode-weight limit for generated classloaders",
                      value -> value.snapshotStats().maxBytecodeWeightBytes());
        registerGauge(meterRegistry, registry, "gear4j.generated.classloaders.protected.over.capacity",
                      "Whether alias-protected classloaders currently exceed count capacity",
                      value -> value.isOverCapacityDueToProtectedLoaders() ? 1.0d : 0.0d);
        registerCounter(meterRegistry, registry, "gear4j.generated.classloaders.evictions",
                        "Generated classloaders evicted from the bounded registry",
                        value -> value.snapshotStats().evictedLoaders());
        registerCounter(meterRegistry, registry, "gear4j.generated.classloaders.rejections",
                        "Generated classloader registrations rejected by the bytecode limit",
                        value -> value.snapshotStats().rejectedLoaders());
    }

    private static void registerGauge(MeterRegistry meterRegistry,
                                      InMemoryClassLoaderRegistry registry,
                                      String name,
                                      String description,
                                      ToDoubleFunction<InMemoryClassLoaderRegistry> valueFunction) {
        Gauge.builder(name, registry, valueFunction)
                .description(description)
                .register(meterRegistry);
    }

    private static void registerCounter(MeterRegistry meterRegistry,
                                        InMemoryClassLoaderRegistry registry,
                                        String name,
                                        String description,
                                        ToDoubleFunction<InMemoryClassLoaderRegistry> valueFunction) {
        FunctionCounter.builder(name, registry, valueFunction)
                .description(description)
                .register(meterRegistry);
    }
}
