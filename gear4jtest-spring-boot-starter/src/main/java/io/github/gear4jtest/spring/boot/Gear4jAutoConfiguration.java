package io.github.gear4jtest.spring.boot;

import javax.sql.DataSource;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.PersistenceRuntimeConfiguration;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.micrometer.PersistenceMetricsBinder;
import io.github.gear4jtest.spring.Gear4jPipelineEngineBuilderCustomizer;
import io.github.gear4jtest.spring.Gear4jSpringConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spring Boot auto-configuration for Gear4J. */
@AutoConfiguration
@EnableConfigurationProperties(Gear4jProperties.class)
@Import(Gear4jSpringConfiguration.class)
public class Gear4jAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Gear4jAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "gear4jParallelExecutionCustomizer")
    Gear4jPipelineEngineBuilderCustomizer gear4jParallelExecutionCustomizer(Gear4jProperties properties) {
        return builder -> builder.parallelExecutionConfiguration(ParallelExecutionConfiguration
                .withDefaultAwaitTimeout(properties.getParallel().getDefaultAwaitTimeout()));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AssemblyRunManager.class)
    @ConditionalOnProperty(prefix = "gear4j.persistence", name = "enabled", havingValue = "true")
    DatabaseExecutionManager gear4jDatabaseExecutionManager(DataSource dataSource,
                                                            Gear4jProperties properties,
                                                            ObjectProvider<SensitiveDataRedactor> redactorProvider) {
        Gear4jProperties.PersistenceProperties persistence = properties.getPersistence();
        persistence.validateWhenEnabled();
        PersistenceRuntimeConfiguration runtimeConfiguration = PersistenceRuntimeConfiguration.builder()
                .batchSize(persistence.getBatchSize())
                .maxPendingLogsPerRun(persistence.getMaxPendingLogsPerRun())
                .flushInterval(persistence.getFlushInterval())
                .shutdownTimeout(persistence.getShutdownTimeout())
                .build();
        SensitiveDataRedactor redactor = redactorProvider.getIfAvailable();
        if (redactor == null) {
            LOGGER.warn("[Gear4J] JDBC persistence is enabled with no SensitiveDataRedactor bean. "
                    + "Pipeline payloads, contexts and results will be persisted as-is.");
            redactor = SensitiveDataRedactor.none();
        }
        return new DatabaseExecutionManager(dataSource, persistence.getDialect(), runtimeConfiguration,
                persistence.isAutoCreateTables(), redactor);
    }

    @Bean
    @ConditionalOnBean(AssemblyRunManager.class)
    @ConditionalOnMissingBean(PersistenceExtension.class)
    PersistenceExtension gear4jPersistenceExtension(AssemblyRunManager manager) {
        return new PersistenceExtension(manager);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(Gear4jMicrometerExtension.class)
    @ConditionalOnProperty(prefix = "gear4j.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    Gear4jMicrometerExtension gear4jMicrometerExtension(MeterRegistry meterRegistry) {
        return new Gear4jMicrometerExtension(meterRegistry);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean({ MeterRegistry.class, DatabaseExecutionManager.class })
    @ConditionalOnProperty(prefix = "gear4j.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    Gear4jPersistenceMetricsRegistrar gear4jPersistenceMetricsRegistrar(MeterRegistry meterRegistry,
                                                                        DatabaseExecutionManager manager) {
        PersistenceMetricsBinder.bind(meterRegistry, manager);
        return new Gear4jPersistenceMetricsRegistrar();
    }

    static final class Gear4jPersistenceMetricsRegistrar {
    }
}
