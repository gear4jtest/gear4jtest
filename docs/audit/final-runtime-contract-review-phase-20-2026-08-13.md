# Final runtime contract review - phase 20

**Date:** 13 August 2026
**Scope:** seven remaining Gear4J 1.0 contract reviews

## Result

All seven 1.0 review items are closed. The review found three runtime defects and
one release-configuration gap; the remaining three contracts already matched the
intended behavior and are now explicitly tested or documented.

| Review | Result | Closure evidence |
| --- | --- | --- |
| Extension ordering, failure and lifecycle | Corrected | Executor wrappers now use lower-order outermost composition. Lifecycle start/completion uses bracket order and attempts all starts before normalizing a critical start failure. |
| Packaging and module naming | Corrected and guarded | The Gradle plugin JAR now carries its declared automatic module name. The release gate requires a valid, distinct `moduleName` and the exact primary-JAR `Automatic-Module-Name`; existing package-marker and Japicmp gates remain unchanged. |
| Skip semantics | Confirmed | Root, unary and intermediate fallback tests prove that condition skips stay `SKIPPED` while continuation output flows downstream. |
| Run timing ownership | Corrected and defined | Start hooks and run interceptors are measured; ordinary completion hooks, final-context copying and event drain are outside the interval. A critical completion failure fixes one terminal timestamp shared by metrics, persistence and the returned result. |
| Exception and fatal boundary | Tightened | Recoverable boundaries normalize `Exception`; `Error` escapes unchanged and bypasses normal completion. `StationExecutionException.unwrap` no longer accepts `Throwable`. |
| Container execution | Confirmed | Tests cover sequential fail-fast/ignore/collect, parallel timeout, completion races, executor rejection, declaration ordering and caller ownership. |
| User cancellation checkpoints | Defined | Gear4J checks controlled station/parallel boundaries; long-running or blocking user code polls the run token and respects interruption/I/O timeouts. |

## Runtime corrections

1. `AssemblyLineRunSupportFactory` now applies executor wrappers in reverse
   construction order, matching run and station wrappers.
2. Run and station lifecycle start callbacks execute from high to low, while
   completion remains low to high. Run start callbacks collect the first
   critical failure instead of abandoning later starts.
3. `Gear4jMicrometerExtension` uses the reserved terminal-observer order;
   `PersistenceExtension` uses the final persistence order.
4. The first critical completion failure fixes the run end time once; the engine
   no longer rewrites it after terminal observers have recorded it.
5. The public unwrap helper accepts only recoverable `Exception` values.

## Contract tests

- `RuntimeExtensionOrderingTest` covers run, station, executor and lifecycle
  ordering plus non-ownership of the raw executor.
- `RunLifecycleExtensionTest` covers timing snapshots and paired callbacks after
  a critical start failure, and proves that persistence and the returned result
  share the same terminal timestamp and error.
- `AssemblyLineEngineFatalErrorTest` proves identity-preserving fatal escape and
  absence of ordinary completion.
- `Gear4jMicrometerLifecycleOrderingTest` proves that critical station/run
  completion failures are measured as `FAILED`.
- `ContainerStationStrategyTest` explicitly proves that Gear4J leaves a
  caller-owned executor running.

## Validation commands

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-core:test \
  --tests '*RuntimeExtensionOrderingTest' \
  --tests '*RunLifecycleExtensionTest' \
  --tests '*AssemblyLineEngineFatalErrorTest' \
  --tests '*ContainerStationStrategyTest'
./gradlew :gear4jtest-micrometer:test \
  --tests '*Gear4jMicrometerLifecycleOrderingTest'
./gradlew check
```

## Validation performed in the phase workspace

- all 282 core production sources compiled with the Java 17 compiler and a
  minimal SLF4J compile-time surface;
- the changed Micrometer production surface compiled against the compiled core
  and a minimal Micrometer compile-time surface;
- all 831 repository Java sources passed an independent syntax parse;
- an executable runtime harness passed wrapper ordering and ownership,
  lifecycle pairing/order, normal and critical timing, skip continuation and
  fatal-`Error` boundary scenarios;
- the repository validator passed all 129 documentation files, ADR identifier
  uniqueness, living-document metadata and text hygiene;
- all 11 declared automatic module names passed the static uniqueness check.

The official Gradle test selection could not start in the phase workspace
because the wrapper distribution download was blocked with `Network is
unreachable`. The commands above remain the authoritative dependency-backed
replay and are required before publication.

The complete connected `releaseCheck`, four-dialect Testcontainers matrix,
reproducibility proof and JReleaser dry-run remain the final release-candidate
qualification. This phase does not add dependency lockfiles, Gradle verification
metadata, durable outbox/spool replay, JPMS descriptors or distributed quotas.
