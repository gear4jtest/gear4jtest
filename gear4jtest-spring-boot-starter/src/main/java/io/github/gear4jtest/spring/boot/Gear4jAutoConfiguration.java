package io.github.gear4jtest.spring.boot;

import javax.sql.DataSource;

import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.PersistenceRuntimeConfiguration;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.micrometer.Gear4jMeterTagPolicy;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.micrometer.PersistenceMetricsBinder;
import io.github.gear4jtest.spring.Gear4jPipelineEngineBuilderCustomizer;
import io.github.gear4jtest.spring.Gear4jSpringConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
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
        SensitiveDataRedactor redactor = resolveRedactor(redactorProvider.getIfAvailable(),
                                                         persistence.getRedactionMode());
        PersistenceRuntimeConfiguration runtimeConfiguration = PersistenceRuntimeConfiguration.builder()
                .batchSize(persistence.getBatchSize())
                .maxPendingLogsPerRun(persistence.getMaxPendingLogsPerRun())
                .flushInterval(persistence.getFlushInterval())
                .shutdownTimeout(persistence.getShutdownTimeout())
                .jdbcStatementTimeout(persistence.getJdbcStatementTimeout())
                .flushThreadCount(persistence.getFlushThreads())
                .maxScheduledFlushTasks(persistence.getMaxScheduledFlushTasks())
                .build();
        return DatabaseExecutionManager.builder()
                .dataSource(dataSource)
                .databaseDialect(persistence.getDialect())
                .configuration(runtimeConfiguration)
                .autoCreateTables(persistence.isAutoCreateTables())
                .redactor(redactor)
                .build();
    }

    private static SensitiveDataRedactor resolveRedactor(SensitiveDataRedactor redactor,
                                                         Gear4jProperties.RedactionMode redactionMode) {
        if (redactionMode == Gear4jProperties.RedactionMode.REQUIRE && SensitiveDataRedactor.isNone(redactor)) {
            throw new IllegalStateException("gear4j.persistence.redaction-mode=REQUIRE requires a "
                    + "SensitiveDataRedactor bean when persistence is enabled");
        }
        if (redactionMode == Gear4jProperties.RedactionMode.DISABLED && SensitiveDataRedactor.isNone(redactor)) {
            return (target, value) -> value;
        }
        return redactor;
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
    Gear4jMicrometerExtension gear4jMicrometerExtension(MeterRegistry meterRegistry,
                                                        ObjectProvider<Gear4jMeterTagPolicy> tagPolicyProvider) {
        return new Gear4jMicrometerExtension(meterRegistry,
                tagPolicyProvider.getIfAvailable(Gear4jMeterTagPolicy::defaults));
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
