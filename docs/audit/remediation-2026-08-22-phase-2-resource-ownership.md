# 2026 audit remediation — phase 2 resource ownership and bounded stores

**Date:** 22 August 2026
**Baseline:** cumulative phase-1 corrected source tree
**Scope:** artifact-store identity/lifecycle, spool quota ownership, bounded
in-memory storage and multi-instance regression tests

## Outcome by audit finding

| Finding | Outcome | Evidence |
| --- | --- | --- |
| F-03 — unbounded and fragmented store lifecycle | Closed for built-in/default-provider paths | provider leases, 256-entry manager cache, balanced manager/checker/reconciler releases, close propagation |
| F-04 — spool quota local to one object | Closed | canonical-directory registry, common counters, policy compatibility, per-process lock and per-JVM default path |
| F-10 — unbounded `MEMORY` backend | Closed | 5 MiB artifact, 64 MiB total and 10,000-entry defaults; configurable finite budgets |
| F-11 — dependency drift and artifact trust | Explicitly out of scope | dependency locking and Gradle verification metadata remain deferred until after 1.0 by the established project policy |
| F-16 — missing multi-instance/concurrency qualification | Closed for phase-2 invariants | concurrent resolver identity from phase 1 plus shared-directory quota, reference-count and deferred-close tests |

## Store ownership and identity

`ArtifactStore` now extends `AutoCloseable` through a compatible no-op default.
`ArtifactStoreProvider.release(...)` is also a default method, preserving the
single abstract method used by lambda providers while making lease ownership
explicit.

`DefaultArtifactStoreProvider` shares a store for equivalent store type/property
maps while leases remain active. It reference-counts acquisitions, closes a
store only after the final release and can close all remaining owned stores at
application shutdown. It never closes an externally supplied data source or
executor.

`AssemblyLineStoreResolver` now uses a synchronized, access-ordered 256-entry
cache. It retains store identity across concurrent initial lookups, counts shared
store references by object identity, and releases provider leases on
configuration replacement, LRU eviction, explicit invalidation and close.
`AssemblyLineManager.close()` includes resolver cleanup even if worker cleanup
fails.

The consistency checker balances its store acquisition in `finally`.
Reconciliation acquires one store per configuration fingerprint for the complete
pass and releases every acquired store. With one shared
`DefaultArtifactStoreProvider`, these operational tools observe the same active
`MEMORY` backend as a manager instead of silently constructing an unrelated map.

## Directory-scoped spool quota

`ArtifactSpoolDirectoryRegistry` owns one state object per canonical directory:

- byte/file occupancy and quota rejection/cleanup counters are global to all
  live store instances in the JVM;
- stores sharing a directory must use identical maximum-byte, stale-age and
  permission policies;
- a private `.gear4j-spool.lock` file prevents another JVM from concurrently
  accounting or cleaning an explicitly configured directory;
- the implicit default uses a runtime-specific subdirectory, avoiding accidental
  cross-process contention;
- every output reservation is reconciled against the real file size on failure
  and close, so a partial filesystem write cannot release bytes that remain on
  disk;
- closing a spool releases its directory lease; already accepted composite
  asynchronous tasks delay delegate/spool closure until their cleanup completes.

The spool remains temporary workspace, not durable replay. No schema or V1
migration change is required.

## Bounded in-memory store

`InMemoryArtifactStore` now rejects distinct content above these defaults:

- 5 MiB per artifact;
- 64 MiB cumulative content;
- 10,000 distinct hashes.

Duplicate content stays idempotent and is not charged twice. Streaming calls
cannot bypass the backend artifact limit with `UNLIMITED_SIZE`. Capacity
exhaustion rejects the write rather than evicting referenced content. The limits
are configurable through the public constructor and the `MEMORY` properties
`maxArtifactSizeBytes`, `maxTotalBytes` and `maxEntries`. Close clears retained
bytes and rejects further use.

## Regression coverage added

- bounded resolver eviction and identity-reference release;
- equivalent default-provider configuration sharing until final lease release;
- checker lease release and manager shutdown release;
- shared spool quota across two live instances;
- rejection of incompatible policies for one directory;
- idempotent spool close and rejection after close;
- bounded memory per-artifact, total-byte, entry and streaming behavior;
- deferred composite close until an accepted asynchronous fallback task ends;
- explicit close in spool-backed JDBC and SPI integration tests.

## Validation in the audit environment

The changed production slices compile with the Java 17 `jdk.compiler` module.
Standalone harnesses using the real changed classes reported:

```text
SPOOL_HARNESS_PASS ArtifactSpoolStats[currentFiles=2, currentBytes=3, maxBytes=5, staleFilesDeleted=0, staleBytesDeleted=0, quotaRejections=1, cleanupFailures=0]
MEMORY_HARNESS_PASS
PROVIDER_HARNESS_PASS
RESOLVER_LIFECYCLE_HARNESS_PASS
SPOOL_CROSS_PROCESS_LOCK_PASS
```

The Gradle wrapper still cannot download Gradle 9.6.1 in this environment
(`java.net.SocketException: Network is unreachable`). Consequently, no JUnit,
Spotless, Checkstyle or full build result is claimed.

Run in the connected project repository:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test \
  --tests '*AssemblyLineStoreResolverTest' \
  --tests '*DefaultArtifactStoreProviderTest' \
  --tests '*ManagedArtifactSpoolTest' \
  --tests '*InMemoryArtifactStoreTest' \
  --tests '*CompositeArtifactStoreTest' \
  --tests '*ArtifactConsistencyCheckerTest' \
  --tests '*ArtifactPublicationReconcilerTest' \
  --tests '*AssemblyLineManagerTest'
./gradlew :gear4jtest-external-jdbc:test \
  --tests '*DatabaseArtifactStoreStreamingTest'
./gradlew check integrationTest dependencyCheckAggregate
```

## Residual risks and next phase

- F-11 remains an accepted and documented residual risk for the 1.0 line.
  Dependency lockfiles and Gradle verification metadata are post-1.0 work and
  are not a pre-release gate.
- Cache invalidation and manager/provider close require application-level
  quiescence; Java cannot revoke a store already handed to an arbitrary caller.
- A process crash can still lose queued asynchronous fallback writes by design.
- Phase 3 owns finite total-work budgets/keyset pagination and schema-aware
  configuration validation (F-06 and F-07).
