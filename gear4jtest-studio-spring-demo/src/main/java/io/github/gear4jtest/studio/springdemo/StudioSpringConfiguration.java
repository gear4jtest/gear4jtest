package io.github.gear4jtest.studio.springdemo;

import javax.sql.DataSource;

import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.spring.Gear4jSpringConfiguration;
import io.github.gear4jtest.studio.demo.DemoDatabase;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.demo.PrefixOperation;
import io.github.gear4jtest.studio.demo.ProductionOnlyOperation;
import io.github.gear4jtest.studio.demo.RequestActorContext;
import io.github.gear4jtest.studio.demo.ResourceSuffixOperation;
import io.github.gear4jtest.studio.demo.TrimOperation;
import io.github.gear4jtest.studio.demo.UppercaseOperation;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

@Configuration(proxyBeanMethods = false)
@Import(Gear4jSpringConfiguration.class)
public class StudioSpringConfiguration {
    @Bean(destroyMethod = "close")
    LocalGear4jRuntime studioRuntime(AssemblyLineExecutor executor,
                                     ResourceFactory resources,
                                     DataCapturePolicy capture) {
        return DemoWiring.runtime("spring", executor, resources, capture);
    }

    @Bean
    StudioManagementService studioService(LocalGear4jRuntime runtime,
                                          DataSource dataSource,
                                          RequestActorContext actors) {
        return DemoDatabase.service(dataSource, runtime, actors);
    }

    @Bean
    RequestActorContext studioActors() {
        return new RequestActorContext();
    }

    @Bean
    @Scope("prototype")
    TrimOperation trim() {
        return new TrimOperation();
    }

    @Bean
    @Scope("prototype")
    UppercaseOperation uppercase() {
        return new UppercaseOperation();
    }

    @Bean
    @Scope("prototype")
    PrefixOperation prefix() {
        return new PrefixOperation();
    }

    @Bean
    @Scope("prototype")
    ResourceSuffixOperation resource() {
        return new ResourceSuffixOperation(DemoWiring.SYNTHETIC_RESOURCES);
    }

    @Bean
    @Scope("prototype")
    ProductionOnlyOperation productionOnly() {
        return new ProductionOnlyOperation();
    }
}
