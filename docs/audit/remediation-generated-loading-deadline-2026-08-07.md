# Generated loading deadline — 7 August 2026

**Finding:** P-01 — generated loading was bounded only during compilation.

## Implemented contract

- `GeneratedLoadingConfiguration` defines a positive complete-load timeout,
  maximum concurrency and bounded queue capacity.
- The default policy is 60 seconds, four workers and 32 queued distinct loads.
- One monotonic deadline starts before executor admission and covers artifact
  access, translation, compilation, class loading, construction and dependency
  injection.
- Owner and joiners share the same single-flight and remaining deadline.
- Timeout, saturation and shutdown remove the in-flight key before waking
  callers, so retry remains possible.
- A worker checks the flight between phases. A component returning after timeout
  cannot continue into later phases, register a classloader or expose an
  instance.
- Cancellation is best-effort. Custom code that ignores interruption can retain
  one daemon worker until it returns; hostile definitions require process or
  container isolation.
- `GeneratedLoadingStats` reports outcomes, saturation and cumulative durations
  for artifact, translation, compilation and instantiation/injection phases.

## Regression coverage

- a non-cooperative translator times out one owner and one joiner;
- late translator output does not reach compilation or the classloader registry;
- a generated constructor finishing after the deadline is not registered;
- a late dependency-injection result is not registered;
- retry succeeds after timeout cleanup;
- a full bounded queue rejects a third distinct load immediately;
- manager-level statistics expose the phase timings.

The connected build remains the release gate:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-external-api:test
./gradlew jacocoModuleCoverageGear4jtestExternalApi
./gradlew check
```
