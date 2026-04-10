# gear4j-spring

`gear4j-spring` integrates Gear4J with the Spring container.

Its goal is not to add Spring Boot magic.
Its goal is to make Gear4J use Spring-managed beans cleanly.

## Why this module exists

`gear4j-core` is framework-agnostic.

It does not know anything about:

* `ApplicationContext`
* Spring bean lookup
* Spring ordering
* Spring configuration classes

`gear4j-spring` provides that integration layer.

## What it provides

The module typically provides:

* a `ResourceFactory` backed by Spring `ApplicationContext`
* a Spring `@Configuration` that creates a `PipelineEngine`
* automatic collection of Gear4J runtime extensions declared as Spring beans
* optional builder customization hooks

## Scope of this module

This module is intentionally kept small.

It is meant for plain Spring integration, not Spring Boot auto-configuration.

That means:

* no `@ConfigurationProperties`
* no `@ConditionalOnClass`
* no starter-style conventions
* no hidden auto-magic

Those concerns belong in a future `gear4j-spring-boot-starter`.

## Basic usage

```java
@Configuration
@Import(Gear4jSpringConfiguration.class)
public class MyGear4jConfiguration {

    @Bean
    public PayloadCloner payloadCloner(ObjectMapper objectMapper) {
        return JacksonPayloadCloners.with(objectMapper);
    }
}
```

With this setup:

* operators can be resolved as Spring beans
* Gear4J runtime extensions can be declared as beans
* the pipeline engine can reuse the application `ObjectMapper` through a `PayloadCloner`

## Typical responsibilities

### Spring-backed resource resolution

Gear4J operators and resources can be instantiated through Spring rather than through manual factories.

### Engine assembly

The module can centralize engine creation in one Spring configuration.

### Extension discovery

Runtime extensions can be discovered automatically from the Spring context.

### Builder customization

Applications can tweak the `PipelineEngine.Builder` without replacing the whole base configuration.

## What this module should not do

This module should not become a catch-all integration layer.

It should avoid:

* Boot-specific auto-configuration
* too many framework assumptions
* environment-driven magic
* hidden defaults that are difficult to reason about

## Future Boot module

A future `gear4j-spring-boot-starter` can provide:

* auto-configuration
* configuration properties
* optional auto-registration of `JacksonPayloadCloner`
* starter-level defaults

Example future property:

```yaml
gear4j:
  payload-cloning-mode: jackson
```
