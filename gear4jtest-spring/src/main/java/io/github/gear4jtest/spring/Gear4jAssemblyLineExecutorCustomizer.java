package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;

/**
 * Stable Spring customization hook for the default Gear4J executor.
 */
@FunctionalInterface
public interface Gear4jAssemblyLineExecutorCustomizer {
    void customize(Builder builder);

    /** Public, provider-neutral subset of runtime builder options. */
    interface Builder {
        Builder parallelExecutionConfiguration(ParallelExecutionConfiguration configuration);

        Builder workerConcurrencyConfiguration(WorkerConcurrencyConfiguration configuration);

        Builder initialRunContextPolicy(ContextPropagationPolicy policy);

        Builder nestedRunContextPropagationPolicy(ContextPropagationPolicy policy);
    }
}
