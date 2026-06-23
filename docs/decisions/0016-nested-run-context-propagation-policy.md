# Nested-run context propagation policy

## Status

Accepted.

## Context

`NESTED_RUN` historically copied the whole parent context map into the child
run with a shallow `new HashMap<>(...)` copy. That gave the child a distinct map,
but mutable values inside the map remained shared references. This was a useful
MVP default, but it made the isolation boundary less explicit than payload
cloning and could surprise applications that store mutable lists, maps or DTOs in
run context.

A deep clone by default would be unsafe: context values can be immutable,
mutable, intentionally shared, externally owned, or not clonable at all.

## Decision

Introduce `ContextPropagationPolicy` and configure it on
`AssemblyLineEngine.Builder` with:

```java
AssemblyLineEngine.builder()
        .nestedRunContextPropagationPolicy(ContextPropagationPolicy.none())
        .build();
```

The default remains `ContextPropagationPolicy.inheritAllShallow()` to preserve
existing behavior.

Built-in policies cover the common cases:

- `inheritAllShallow()` copies the map but shares values by reference;
- `none()` propagates no user context;
- `includeKeys(...)` propagates a key allow-list with shallow values;
- `copyValues(...)` lets applications clone, transform or omit values explicitly.

`NESTED_RUN` still deliberately shares the parent cancellation token and call
stack. The new policy only controls user context key/value propagation.

## Consequences

Nested runs now have an explicit context isolation contract without introducing a
fragile generic deep clone. Applications can keep the previous behavior, disable
context inheritance entirely, or copy only the mutable values they understand.

A policy that returns `null` is treated as an empty context. A policy that returns
null keys or values is rejected before creating the child `RunRequest`, because
Gear4J runtime contexts do not support null context entries.
