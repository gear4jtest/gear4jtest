package io.github.gear4jtest.spring;

import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Base Spring integration for Gear4J.
 *
 * <p>
 * This module intentionally focuses on plain Spring integration only.
 * Boot-specific auto-configuration should live in a dedicated starter module.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class Gear4jSpringConfiguration {
    @Bean
    ResourceFactory gear4jResourceFactory(ApplicationContext applicationContext) {
        return new SpringResourceFactory(applicationContext);
    }

    @Bean
    ExecutionContextRegistry gear4jExecutionContextRegistry() {
        return new ExecutionContextRegistry();
    }

    @Bean
    RuntimeExtensionResolver gear4jRuntimeExtensionResolver(
                                                            ObjectProvider<RuntimeExtension> runtimeExtensionsProvider) {

        List<RuntimeExtension> runtimeExtensions = runtimeExtensionsProvider.orderedStream()
                .sorted(Comparator
                        .comparingInt(RuntimeExtension::getOrder)
                        .thenComparing(extension -> extension.getClass().getName()))
                .toList();

        return new RuntimeExtensionResolver(runtimeExtensions);
    }

    @Bean
    AssemblyLineRegistry gear4jAssemblyLineRegistry(ListableBeanFactory beanFactory) {
        return new SpringAssemblyLineRegistry(beanFactory);
    }

    @Bean
    AssemblyLineExecutor gear4jAssemblyLineExecutor(
                                                    ResourceFactory resourceFactory,
                                                    ObjectProvider<RunnerChainFactory> runnerChainFactoryProvider,
                                                    RuntimeExtensionResolver extensionResolver,
                                                    ExecutionContextRegistry executionContextRegistry,
                                                    ObjectProvider<IdGenerator> idGeneratorProvider,
                                                    ObjectProvider<TaskFactory> taskFactoryProvider,
                                                    ObjectProvider<PayloadCloner> payloadClonerProvider,
                                                    ObjectProvider<Gear4jAssemblyLineExecutorCustomizer> customizersProvider) {

        AssemblyLineEngine.Builder builder = AssemblyLineEngine.builder().resourceFactory(resourceFactory)
                .extensionResolver(extensionResolver).executionContextRegistry(executionContextRegistry);

        RunnerChainFactory runnerChainFactory = runnerChainFactoryProvider.getIfAvailable();
        if (runnerChainFactory != null) {
            builder.runnerChainFactory(runnerChainFactory);
        }

        IdGenerator idGenerator = idGeneratorProvider.getIfAvailable();
        if (idGenerator != null) {
            builder.idGenerator(idGenerator);
        }

        TaskFactory taskFactory = taskFactoryProvider.getIfAvailable();
        if (taskFactory != null) {
            builder.taskFactory(taskFactory);
        }

        PayloadCloner payloadCloner = payloadClonerProvider.getIfAvailable();
        if (payloadCloner != null) {
            builder.payloadCloner(payloadCloner);
        }

        MutableExecutorOptions options = new MutableExecutorOptions();
        for (Gear4jAssemblyLineExecutorCustomizer customizer : customizersProvider.orderedStream().toList()) {
            customizer.customize(options);
        }
        options.applyTo(builder);

        return builder.build();
    }

    private static final class MutableExecutorOptions implements Gear4jAssemblyLineExecutorCustomizer.Builder {
        private ParallelExecutionConfiguration parallelExecutionConfiguration;
        private WorkerConcurrencyConfiguration workerConcurrencyConfiguration;
        private ContextPropagationPolicy initialRunContextPolicy;
        private ContextPropagationPolicy nestedRunContextPropagationPolicy;

        @Override
        public Gear4jAssemblyLineExecutorCustomizer.Builder parallelExecutionConfiguration(
                                                                                           ParallelExecutionConfiguration configuration) {
            this.parallelExecutionConfiguration = configuration;
            return this;
        }

        @Override
        public Gear4jAssemblyLineExecutorCustomizer.Builder workerConcurrencyConfiguration(
                                                                                           WorkerConcurrencyConfiguration configuration) {
            this.workerConcurrencyConfiguration = configuration;
            return this;
        }

        @Override
        public Gear4jAssemblyLineExecutorCustomizer.Builder initialRunContextPolicy(ContextPropagationPolicy policy) {
            this.initialRunContextPolicy = policy;
            return this;
        }

        @Override
        public Gear4jAssemblyLineExecutorCustomizer.Builder nestedRunContextPropagationPolicy(
                                                                                              ContextPropagationPolicy policy) {
            this.nestedRunContextPropagationPolicy = policy;
            return this;
        }

        private void applyTo(AssemblyLineEngine.Builder builder) {
            if (parallelExecutionConfiguration != null) {
                builder.parallelExecutionConfiguration(parallelExecutionConfiguration);
            }
            if (workerConcurrencyConfiguration != null) {
                builder.workerConcurrencyConfiguration(workerConcurrencyConfiguration);
            }
            if (initialRunContextPolicy != null) {
                builder.initialRunContextPolicy(initialRunContextPolicy);
            }
            if (nestedRunContextPropagationPolicy != null) {
                builder.nestedRunContextPropagationPolicy(nestedRunContextPropagationPolicy);
            }
        }
    }
}
