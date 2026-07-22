package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.annotation.Internal;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

@Internal
final class DefaultAssemblyLineExecutorBuilder implements AssemblyLineExecutorBuilder {
    private final AssemblyLineEngine.Builder delegate = AssemblyLineEngine.builder()
            .executionContextRegistry(new ExecutionContextRegistry());
    private List<RuntimeExtension> runtimeExtensions = List.of();

    @Override
    public AssemblyLineExecutorBuilder resourceFactory(ResourceFactory resourceFactory) {
        delegate.resourceFactory(resourceFactory);
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder runtimeExtensions(List<? extends RuntimeExtension> runtimeExtensions) {
        if (runtimeExtensions == null) {
            this.runtimeExtensions = List.of();
        } else {
            this.runtimeExtensions = List.copyOf(new ArrayList<>(runtimeExtensions));
        }
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder idGenerator(IdGenerator idGenerator) {
        delegate.idGenerator(Objects.requireNonNull(idGenerator, "idGenerator must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder payloadCloner(PayloadCloner payloadCloner) {
        delegate.payloadCloner(Objects.requireNonNull(payloadCloner, "payloadCloner must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder workerConcurrencyConfiguration(
                                                                      WorkerConcurrencyConfiguration workerConcurrencyConfiguration) {
        delegate.workerConcurrencyConfiguration(Objects.requireNonNull(workerConcurrencyConfiguration,
                                                                       "workerConcurrencyConfiguration must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder parallelExecutionConfiguration(
                                                                      ParallelExecutionConfiguration parallelExecutionConfiguration) {
        delegate.parallelExecutionConfiguration(Objects.requireNonNull(parallelExecutionConfiguration,
                                                                       "parallelExecutionConfiguration must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder initialRunContextPolicy(ContextPropagationPolicy initialRunContextPolicy) {
        delegate.initialRunContextPolicy(Objects.requireNonNull(initialRunContextPolicy,
                                                                "initialRunContextPolicy must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutorBuilder nestedRunContextPropagationPolicy(
                                                                         ContextPropagationPolicy nestedRunContextPropagationPolicy) {
        delegate.nestedRunContextPropagationPolicy(Objects.requireNonNull(nestedRunContextPropagationPolicy,
                                                                          "nestedRunContextPropagationPolicy must not be null"));
        return this;
    }

    @Override
    public AssemblyLineExecutor build() {
        delegate.extensionResolver(new RuntimeExtensionResolver(runtimeExtensions));
        return delegate.build();
    }
}
