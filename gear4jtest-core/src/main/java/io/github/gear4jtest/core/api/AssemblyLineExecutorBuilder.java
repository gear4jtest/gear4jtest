package io.github.gear4jtest.core.api;

import java.util.List;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

/**
 * Public builder for the default Gear4J execution runtime.
 *
 * <p>
 * The concrete engine remains an implementation detail. Applications can
 * construct an executor through this contract without importing an internal
 * runtime package.
 * </p>
 */
public interface AssemblyLineExecutorBuilder {
    AssemblyLineExecutorBuilder resourceFactory(ResourceFactory resourceFactory);

    AssemblyLineExecutorBuilder runtimeExtensions(List<? extends RuntimeExtension> runtimeExtensions);

    default AssemblyLineExecutorBuilder runtimeExtensions(RuntimeExtension... runtimeExtensions) {
        return runtimeExtensions(runtimeExtensions == null ? List.of() : List.of(runtimeExtensions));
    }

    AssemblyLineExecutorBuilder idGenerator(IdGenerator idGenerator);

    AssemblyLineExecutorBuilder payloadCloner(PayloadCloner payloadCloner);

    AssemblyLineExecutorBuilder workerConcurrencyConfiguration(
                                                               WorkerConcurrencyConfiguration workerConcurrencyConfiguration);

    AssemblyLineExecutorBuilder parallelExecutionConfiguration(
                                                               ParallelExecutionConfiguration parallelExecutionConfiguration);

    AssemblyLineExecutorBuilder initialRunContextPolicy(ContextPropagationPolicy initialRunContextPolicy);

    AssemblyLineExecutorBuilder nestedRunContextPropagationPolicy(
                                                                  ContextPropagationPolicy nestedRunContextPropagationPolicy);

    AssemblyLineExecutor build();
}
