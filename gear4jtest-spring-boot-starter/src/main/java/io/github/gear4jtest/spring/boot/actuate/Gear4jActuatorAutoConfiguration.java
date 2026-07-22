package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.spring.boot.Gear4jAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Optional Spring Boot Actuator integration for Gear4J. */
@AutoConfiguration(after = Gear4jAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
public class Gear4jActuatorAutoConfiguration {
    @Bean(name = { "gear4jPersistenceReadinessIndicator", "gear4jPersistenceHealthIndicator" })
    @ConditionalOnBean(PersistenceRuntimeMonitor.class)
    @ConditionalOnMissingBean(name = { "gear4jPersistenceReadinessIndicator",
            "gear4jPersistenceHealthIndicator" })
    Gear4jPersistenceHealthIndicator gear4jPersistenceReadinessIndicator(PersistenceRuntimeMonitor manager) {
        return new Gear4jPersistenceHealthIndicator(manager);
    }

    @Bean
    @ConditionalOnBean(PersistenceRuntimeMonitor.class)
    @ConditionalOnMissingBean(name = "gear4jPersistenceLivenessIndicator")
    Gear4jPersistenceLivenessIndicator gear4jPersistenceLivenessIndicator(PersistenceRuntimeMonitor manager) {
        return new Gear4jPersistenceLivenessIndicator(manager);
    }
}
