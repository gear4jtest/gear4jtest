# gear4jtest-experimental-cache

Experimental cache and history helpers for Gear4J assembly lines.

This module is intentionally unstable and opt-in. It contains reusable cache helpers that were previously test-only code
inside `gear4jtest-core`. The artifact is published with Gear4J 1.0, but its `@Experimental` API is excluded from the
stable compatibility contract and may change or be removed in a later release. Production applications should isolate
their dependency on this module behind an application-owned adapter.

`JsonSha256FingerprintStrategy` exposes an optional `ObjectMapper` constructor for callers that need application-specific
serialization. Jackson is therefore a published API dependency of this module; consumers do not need to redeclare
`jackson-databind` merely to call that constructor.

## In-memory repository contract

`InMemoryAssemblyLineCacheRepository` is thread-safe, access-ordered and bounded. Its no-argument constructor retains at
most 1,024 entries, removes expired entries during reads/writes and accepts only outputs supported by the strict
immutable-aware `PayloadCloner`.

Mutable outputs require an explicit cloner. The cloner is invoked before storage and again for every cache hit, so a
caller cannot mutate the value retained for another run:

```java
var repository = new InMemoryAssemblyLineCacheRepository(
        500,
        64 * 1024 * 1024L,
        applicationPayloadCloner,
        (key, output) -> applicationWeightOf(output));
```

An entry that cannot be cloned, has an invalid weight or exceeds the maximum weight is skipped without turning the
successful pipeline run into a failure. Observe that condition with `repository.snapshotStats().rejectedWrites()`.
The same snapshot exposes hits, misses, writes, TTL/capacity evictions, current entries/weight and cache-miss load time.

The default weight is one unit per entry. Applications using large cached objects should supply a domain-specific
weigher and a maximum weight based on an operational memory budget.
