# gear4jtest-spring

`gear4jtest-spring` integrates Gear4J with the Spring container.

Its goal is not to add Spring Boot magic. Its goal is to let Gear4J use Spring-managed beans cleanly while keeping the
core engine framework-agnostic.

## Why this module exists

`gear4jtest-core` does not know about:

- `ApplicationContext`;
- Spring bean lookup;
- Spring ordering;
- Spring configuration classes.

This module provides that integration layer.

## What it provides

The module typically provides:

- a `ResourceFactory` backed by Spring `ApplicationContext`;
- a Spring `@Configuration` that creates a `PipelineEngine`;
- automatic collection of Gear4J runtime extensions declared as Spring beans;
- builder customization hooks.

## Scope

This module is intentionally small and should stay plain-Spring oriented.

It should avoid:

- Spring Boot auto-configuration;
- `@ConfigurationProperties`;
- `@ConditionalOnClass` conventions;
- starter-style hidden defaults;
- environment-driven magic.

Those concerns belong in a future `gear4jtest-spring-boot-starter`.

## Basic usage shape

```java
@Configuration
@Import(Gear4jSpringConfiguration.class)
public class MyGear4jConfiguration {

    @Bean
    PayloadCloner payloadCloner(ObjectMapper objectMapper) {
        return JacksonPayloadCloners.with(objectMapper);
    }
}
```

With this setup:

- operators can be resolved as Spring beans;
- Gear4J runtime extensions can be declared as beans;
- the pipeline engine can reuse application-level collaborators.

## Typical responsibilities

### Spring-backed resource resolution

Gear4J operators and resources can be instantiated or looked up through Spring.

### Engine assembly

The module can centralize engine creation in one Spring configuration.

### Extension discovery

Runtime extensions can be discovered automatically from the Spring context.

### Builder customization

Applications can tweak the `PipelineEngine.Builder` without replacing the whole base configuration.

## Future Boot module

A future Boot starter can provide:

- auto-configuration;
- configuration properties;
- optional auto-registration of `JacksonPayloadCloner`;
- starter-level defaults.

## Testing

Useful focused task:

```bash
./gradlew :gear4jtest-spring:test
```

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
