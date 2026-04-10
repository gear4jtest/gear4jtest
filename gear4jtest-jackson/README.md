# gear4j-jackson

`gear4j-jackson` provides a Jackson-based implementation of Gear4J’s `PayloadCloner` SPI.

Its role is to enable safe payload isolation for mutable DTOs, POJOs, lists, sets, maps and arrays, without introducing any Jackson dependency into `gear4j-core`.

## Why this module exists

`gear4j-core` intentionally stays dependency-agnostic.

This means the core engine does not know how to deep-clone arbitrary business objects.
That responsibility is delegated to an SPI: `PayloadCloner`.

`gear4j-jackson` is one optional implementation of that SPI.

## What it provides

The module provides a `JacksonPayloadCloner` that:

* returns known immutable values as-is
* recursively clones arrays
* recursively clones lists, sets and maps
* clones regular POJOs using Jackson
* throws `PayloadCloneException` when cloning fails

## Dependency example

Gradle:

```groovy
dependencies {
    implementation project(':gear4jtest-core')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
}
```

## Basic usage

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.jackson.JacksonPayloadCloners;

ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

PayloadCloner payloadCloner = JacksonPayloadCloners.with(objectMapper);

PipelineEngine engine = PipelineEngine.builder()
        .resourceFactory(resourceFactory)
        .runnerChainFactory(runnerChainFactory)
        .extensionResolver(extensionResolver)
        .executionContextRegistry(executionContextRegistry)
        .payloadCloner(payloadCloner)
        .build();
```

## When to use this module

Use `gear4j-jackson` when your pipeline payloads are mutable and branch isolation matters.

Typical cases:

* DTO input objects
* mutable POJOs
* lists or maps used as payloads
* parallel containers
* sequential containers where each branch must receive an isolated copy

## Behavior

### Immutable values

Known immutable values are shared as-is.

This avoids wasting CPU on types that are already safe to share, such as:

* `String`
* boxed primitives
* `UUID`
* `BigDecimal`
* Java Time immutable types
* enums

### Arrays, lists, sets and maps

Top-level arrays, collections and maps are cloned structurally and recursively.

This is important because a naïve Jackson clone based only on `payload.getClass()` is often not enough to preserve element runtime types for raw or top-level collections.

### POJOs

Regular mutable objects are cloned using Jackson and the configured `ObjectMapper`.

This means the quality of cloning depends on the mapper configuration:

* registered modules
* Java Time support
* polymorphic typing
* mixins
* naming strategy
* visibility rules

## Limitations

This module is intentionally pragmatic.

Known limitations:

* cyclic POJO graphs are not guaranteed to be supported transparently
* polymorphic models may require a specifically configured `ObjectMapper`
* objects that cannot be round-tripped by Jackson cannot be cloned by this module
* some exotic collection implementations may be recreated using standard mutable implementations instead of the exact original concrete type

## Recommended practice

Prefer reusing your application’s main `ObjectMapper` rather than creating a new one only for Gear4J.

That keeps clone behavior aligned with the rest of your application.

## Unsafe alternative

If you explicitly want to bypass cloning, use the core unsafe mode:

```java
.payloadCloner(PayloadCloners.noOpUnsafe())
```

This should only be used when payload mutability is fully controlled by the caller.