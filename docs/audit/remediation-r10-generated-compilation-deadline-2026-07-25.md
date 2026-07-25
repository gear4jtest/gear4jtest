# Remediation R10 — Generated compilation deadline and isolation

**Date:** 25 July 2026
**Finding:** F-15

## Scope

R10 bounds the duration and resource consumption of runtime generated-source
compilation. It does not change the trusted-content model, replace Eclipse JDT
internals or add Micrometer dashboards; those remain separate findings.

## Implemented contract

- `GeneratedCompilationConfiguration` defines a positive end-to-end timeout,
  maximum concurrent delegate calls and bounded queue capacity.
- Defaults are 30 seconds, one worker and 32 queued distinct compilations.
- `BoundedGeneratedSourceCompiler` executes delegates outside caller threads.
- The timeout includes queue wait and is enforced by a separate daemon scheduler,
  so a saturated or non-cooperative compiler cannot block all calling threads
  indefinitely.
- One deadline terminates the shared single-flight. Owner and waiters observe
  `CompilationTimeoutException`, the in-flight key is removed and retry is
  possible.
- Delegate interruption is best-effort. Bytecode returned after timeout is
  discarded and never cached.
- Executor saturation fails immediately and is counted.
- `GeneratedCompilationStats` exposes cache hits/misses, single-flight joins,
  started/successful/failed/timed-out/rejected compilations, cache size,
  active/queued work and delegate durations.
- `AssemblyLineManager` implements `AutoCloseable` and closes its owned workers.

## Validation

The focused tests cover:

- default and invalid configuration;
- completed-cache reuse and defensive copies;
- single-flight success;
- owner timeout and waiter wake-up;
- a delegate that deliberately ignores interruption;
- in-flight cleanup, late-result rejection and successful retry;
- bounded-queue rejection;
- shutdown cancellation and post-close rejection;
- propagation through `GeneratedAssemblyLineLoader`;
- manager-level cache and statistics visibility.

The full Gradle validation remains required on a connected host:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test
./gradlew check
```
