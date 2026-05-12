package io.github.gear4jtest.spring;

import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
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
    public ResourceFactory gear4jResourceFactory(ApplicationContext applicationContext) {
        return new SpringResourceFactory(applicationContext);
    }

    @Bean
    public ExecutionContextRegistry gear4jExecutionContextRegistry() {
        return new ExecutionContextRegistry();
    }

    @Bean
    public StrategyRegistry gear4jStrategyRegistry() {
        return StrategyRegistry.defaultRegistry();
    }

    @Bean
    public RunnerChainFactory gear4jRunnerChainFactory(StrategyRegistry strategyRegistry) {
        return new RunnerChainFactory(strategyRegistry);
    }

    @Bean
    public RuntimeExtensionResolver gear4jRuntimeExtensionResolver(ObjectProvider<RuntimeExtension> runtimeExtensionsProvider) {

        List<RuntimeExtension> runtimeExtensions = runtimeExtensionsProvider.orderedStream().sorted(Comparator
                .comparingInt(RuntimeExtension::getOrder).thenComparing(extension -> extension.getClass().getName()))
                .toList();

        return new RuntimeExtensionResolver(runtimeExtensions);
    }

    @Bean
    public AssemblyLineRegistry gear4jAssemblyLineRegistry(ListableBeanFactory beanFactory) {
        return new SpringAssemblyLineRegistry(beanFactory);
    }

    @Bean
    public PipelineEngine gear4jPipelineEngine(ResourceFactory resourceFactory,
                                               RunnerChainFactory runnerChainFactory,
                                               RuntimeExtensionResolver extensionResolver,
                                               ExecutionContextRegistry executionContextRegistry,
                                               ObjectProvider<IdGenerator> idGeneratorProvider,
                                               ObjectProvider<TaskFactory> taskFactoryProvider,
                                               ObjectProvider<PayloadCloner> payloadClonerProvider,
                                               ObjectProvider<Gear4jPipelineEngineBuilderCustomizer> customizersProvider) {

        PipelineEngine.Builder builder = PipelineEngine.builder().resourceFactory(resourceFactory)
                .runnerChainFactory(runnerChainFactory).extensionResolver(extensionResolver)
                .executionContextRegistry(executionContextRegistry);

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

        for (Gear4jPipelineEngineBuilderCustomizer customizer : customizersProvider.orderedStream().toList()) {
            customizer.customize(builder);
        }

        return builder.build();
    }
}
