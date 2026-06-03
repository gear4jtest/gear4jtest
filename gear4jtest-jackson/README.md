# gear4jtest-jackson

`gear4jtest-jackson` provides a Jackson-based implementation of Gear4J's `PayloadCloner` SPI.

Its role is to enable safer payload isolation for mutable DTOs, POJOs, lists, sets, maps and arrays, without introducing
Jackson-specific cloning behavior into `gear4jtest-core`.

## Why this module exists

`gear4jtest-core` intentionally stays dependency-agnostic.

The core engine does not know how to deep-clone arbitrary business objects. That responsibility is delegated to the
`PayloadCloner` SPI.

`gear4jtest-jackson` is one optional implementation of that SPI.

## What it provides

The module provides a `JacksonPayloadCloner` that:

- returns known immutable values as-is;
- recursively clones arrays;
- recursively clones lists, sets and maps;
- clones regular POJOs using Jackson;
- throws `PayloadCloneException` when cloning fails.

## Basic usage

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.jackson.JacksonPayloadCloners;

ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
PayloadCloner payloadCloner = JacksonPayloadCloners.with(objectMapper);
```

Pass the cloner to the `PipelineEngine` builder where the engine is assembled.

## When to use this module

Use this module when pipeline payloads are mutable and branch isolation matters.

Typical cases:

- DTO input objects;
- mutable POJOs;
- lists or maps used as payloads;
- parallel containers;
- sequential containers where each branch must receive an isolated copy.

## Behavior

Known immutable values are shared as-is to avoid unnecessary CPU work.

Arrays, collections and maps are cloned structurally and recursively.

Regular mutable objects are cloned using the configured `ObjectMapper`.

## Limitations

This module is intentionally pragmatic.

Known limitations:

- cyclic POJO graphs are not guaranteed to be supported transparently;
- polymorphic models may require a specifically configured `ObjectMapper`;
- objects that cannot be round-tripped by Jackson cannot be cloned by this module;
- exotic collection implementations may be recreated using standard mutable implementations instead of the exact
  original concrete type.

## Recommended practice

Reuse your application's main `ObjectMapper` rather than creating a new one only for Gear4J.

That keeps clone behavior aligned with the rest of the application.

## Unsafe alternative

If cloning should be bypassed explicitly, use the core unsafe mode:

```java
.payloadCloner(PayloadCloners.noOpUnsafe())
```

Only use this when payload mutability is fully controlled by the caller.

## Testing

Useful focused task:

```bash
./gradlew :gear4jtest-jackson:test
```

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.
