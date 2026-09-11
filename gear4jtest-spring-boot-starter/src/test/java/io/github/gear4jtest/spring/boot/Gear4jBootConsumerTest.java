package io.github.gear4jtest.spring.boot;

import javax.sql.DataSource;

import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.jdbc.execution.DatabaseExecutionManager;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import io.github.gear4jtest.micrometer.Gear4jMicrometerExtension;
import io.github.gear4jtest.spring.boot.actuate.Gear4jActuatorAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class Gear4jBootConsumerTest {
    // No manually supplied DataSource, transaction manager or MeterRegistry.
    private final ApplicationContextRunner consumer = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Gear4jAutoConfiguration.class,
                                                     Gear4jActuatorAutoConfiguration.class,
                                                     DataSourceAutoConfiguration.class,
                                                     DataSourceTransactionManagerAutoConfiguration.class,
                                                     MetricsAutoConfiguration.class,
                                                     CompositeMeterRegistryAutoConfiguration.class,
                                                     SimpleMetricsExportAutoConfiguration.class))
            .withPropertyValues("spring.datasource.generate-unique-name=true",
                                "gear4j.persistence.enabled=true", "gear4j.persistence.dialect=H2",
                                "gear4j.persistence.auto-create-tables=true");

    @Test
    void bootConsumer_shouldInstallPersistenceTransactionsMetricsAndHealth() {
        // Given / When / Then
        consumer.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(DataSource.class)
                    .hasSingleBean(DataSourceTransactionManager.class)
                    .hasSingleBean(RunPersistenceManager.class)
                    .hasSingleBean(PersistenceExtension.class)
                    .hasSingleBean(Gear4jMicrometerExtension.class)
                    .hasSingleBean(AssemblyLineExecutor.class)
                    .hasBean("gear4jPersistenceReadinessIndicator")
                    .hasBean("gear4jPersistenceMetricsRegistrar")
                    .hasBean("gear4jEventMetricsRegistrar");
            assertThat(context.getBean(JdbcTransactionOperations.class))
                    .isInstanceOf(SpringJdbcTransactionOperations.class);
            assertThat(context.getBean(MeterRegistry.class).getMeters()).isNotEmpty();
        });
    }

    @Test
    void bootConsumer_shouldRespectDisabledPersistenceAndMetricsWithBootInfrastructurePresent() {
        // Given / When / Then
        consumer.withPropertyValues("gear4j.persistence.enabled=false", "gear4j.metrics.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(DataSource.class)
                        .hasSingleBean(DataSourceTransactionManager.class)
                        .hasSingleBean(AssemblyLineExecutor.class)
                        .doesNotHaveBean(DatabaseExecutionManager.class)
                        .doesNotHaveBean(JdbcTransactionOperations.class)
                        .doesNotHaveBean(Gear4jMicrometerExtension.class)
                        .doesNotHaveBean("gear4jPersistenceMetricsRegistrar")
                        .doesNotHaveBean("gear4jEventMetricsRegistrar"));
    }

    @Test
    void coreConsumer_shouldStartWithoutActuatorClasses() {
        // Given / When / Then
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .withConfiguration(AutoConfigurations.of(Gear4jAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(AssemblyLineExecutor.class));
    }
}
