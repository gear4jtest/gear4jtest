package io.github.gear4jtest.spring.boot;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import io.github.gear4jtest.jdbc.execution.DatabaseExecutionManager;
import io.github.gear4jtest.jdbc.execution.PersistenceRuntimeConfiguration;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import io.github.gear4jtest.jdbc.persistence.PersistenceJsonCodec;
import io.github.gear4jtest.micrometer.EventMetricsBinder;
import io.github.gear4jtest.micrometer.Gear4jMeterTagPolicy;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.micrometer.PersistenceMetricsBinder;
import io.github.gear4jtest.spring.Gear4jAssemblyLineExecutorCustomizer;
import io.github.gear4jtest.spring.Gear4jSpringConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Spring Boot auto-configuration for Gear4J. */
@AutoConfiguration
@EnableConfigurationProperties(Gear4jProperties.class)
@Import(Gear4jSpringConfiguration.class)
public class Gear4jAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "gear4jParallelExecutionCustomizer")
    Gear4jAssemblyLineExecutorCustomizer gear4jParallelExecutionCustomizer(Gear4jProperties properties) {
        return builder -> builder.parallelExecutionConfiguration(ParallelExecutionConfiguration
                .withDefaultAwaitTimeout(properties.getParallel().getDefaultAwaitTimeout()));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(RunPersistenceManager.class)
    @ConditionalOnProperty(prefix = "gear4j.persistence", name = "enabled", havingValue = "true")
    DatabaseExecutionManager gear4jDatabaseExecutionManager(DataSource dataSource,
                                                            Gear4jProperties properties,
                                                            ObjectProvider<SensitiveDataRedactor> redactorProvider,
                                                            ObjectProvider<PayloadCloner> payloadClonerProvider,
                                                            ObjectProvider<PersistenceJsonCodec> jsonCodecProvider,
                                                            ObjectProvider<JdbcTransactionOperations> jdbcTransactions,
                                                            ObjectProvider<ObjectMapper> objectMapperProvider) {
        Gear4jProperties.PersistenceProperties persistence = properties.getPersistence();
        persistence.validateWhenEnabled();
        SensitiveDataRedactor redactor = resolveRedactor(redactorProvider.getIfAvailable(),
                                                         persistence.getRedactionMode());
        PersistenceRuntimeConfiguration runtimeConfiguration = PersistenceRuntimeConfiguration.builder()
                .batchSize(persistence.getBatchSize())
                .maxPendingLogsPerRun(persistence.getMaxPendingLogsPerRun())
                .flushInterval(persistence.getFlushInterval())
                .shutdownTimeout(persistence.getShutdownTimeout())
                .shutdownRetryInitialBackoff(persistence.getShutdownRetryInitialBackoff())
                .shutdownRetryMaxBackoff(persistence.getShutdownRetryMaxBackoff())
                .jdbcStatementTimeout(persistence.getJdbcStatementTimeout())
                .flushThreadCount(persistence.getFlushThreads())
                .maxScheduledFlushTasks(persistence.getMaxScheduledFlushTasks())
                .readinessMaxBufferedStationLogs(persistence.getReadinessMaxBufferedStationLogs())
                .readinessMaxBacklogAge(persistence.getReadinessMaxBacklogAge())
                .connectivityProbeTimeout(persistence.getConnectivityProbeTimeout())
                .build();
        DatabaseExecutionManager.Builder managerBuilder = DatabaseExecutionManager.builder()
                .dataSource(dataSource)
                .databaseDialect(persistence.getDialect())
                .configuration(runtimeConfiguration)
                .autoCreateTables(persistence.isAutoCreateTables())
                .baselineOnMigrate(persistence.isBaselineOnMigrate())
                .redactor(redactor);
        payloadClonerProvider.ifAvailable(managerBuilder::payloadCloner);
        jdbcTransactions.ifAvailable(managerBuilder::transactionOperations);
        PersistenceJsonCodec jsonCodec = jsonCodecProvider.getIfAvailable();
        if (jsonCodec != null) {
            managerBuilder.jsonCodec(jsonCodec);
        } else {
            objectMapperProvider.ifAvailable(managerBuilder::objectMapper);
        }
        return managerBuilder.build();
    }

    @Bean
    @ConditionalOnSingleCandidate(DataSourceTransactionManager.class)
    @ConditionalOnMissingBean(JdbcTransactionOperations.class)
    @ConditionalOnProperty(prefix = "gear4j.persistence", name = "enabled", havingValue = "true")
    SpringJdbcTransactionOperations gear4jJdbcTransactionOperations(DataSource dataSource,
                                                                    DataSourceTransactionManager transactionManager) {
        return new SpringJdbcTransactionOperations(dataSource, transactionManager);
    }

    @SuppressWarnings("removal")
    static SensitiveDataRedactor resolveRedactor(SensitiveDataRedactor redactor,
                                                 Gear4jProperties.RedactionMode redactionMode) {
        if (redactor != null) {
            if (redactionMode == Gear4jProperties.RedactionMode.REQUIRE
                    && SensitiveDataRedactor.isNone(redactor)) {
                throw missingRequiredRedactor();
            }
            return redactor;
        }
        return switch (redactionMode) {
            case DISCARD -> SensitiveDataRedactor.discardSensitiveValues();
            case REQUIRE -> throw missingRequiredRedactor();
            case WARN -> SensitiveDataRedactor.none();
            case DISABLED -> (target, value) -> value;
        };
    }

    private static IllegalStateException missingRequiredRedactor() {
        return new IllegalStateException("gear4j.persistence.redaction-mode=REQUIRE requires a "
                + "SensitiveDataRedactor bean when persistence is enabled");
    }

    @Bean
    @ConditionalOnBean(RunPersistenceManager.class)
    @ConditionalOnMissingBean(PersistenceExtension.class)
    PersistenceExtension gear4jPersistenceExtension(RunPersistenceManager manager) {
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
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "gear4j.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    Gear4jEventMetricsRegistrar gear4jEventMetricsRegistrar(MeterRegistry meterRegistry) {
        EventMetricsBinder.bindProcessWide(meterRegistry);
        return new Gear4jEventMetricsRegistrar();
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean({ MeterRegistry.class, PersistenceRuntimeMonitor.class })
    @ConditionalOnProperty(prefix = "gear4j.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    Gear4jPersistenceMetricsRegistrar gear4jPersistenceMetricsRegistrar(MeterRegistry meterRegistry,
                                                                        PersistenceRuntimeMonitor manager) {
        PersistenceMetricsBinder.bind(meterRegistry, manager);
        return new Gear4jPersistenceMetricsRegistrar();
    }

    static final class Gear4jPersistenceMetricsRegistrar {
    }

    static final class Gear4jEventMetricsRegistrar {
    }
}
