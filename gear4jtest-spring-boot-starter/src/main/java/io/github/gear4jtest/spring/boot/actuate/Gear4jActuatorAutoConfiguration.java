package io.github.gear4jtest.spring.boot.actuate;

import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
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
    @Bean
    @ConditionalOnBean(PersistenceRuntimeMonitor.class)
    @ConditionalOnMissingBean(name = "gear4jPersistenceHealthIndicator")
    Gear4jPersistenceHealthIndicator gear4jPersistenceHealthIndicator(PersistenceRuntimeMonitor manager) {
        return new Gear4jPersistenceHealthIndicator(manager);
    }
}
