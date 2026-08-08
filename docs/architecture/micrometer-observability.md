# Micrometer observability

## Status

Implemented for the critical operational surface.

The `gear4jtest-micrometer` module is an optional integration. It provides
lifecycle counters/timers plus low-level signals for persistence, event runtime,
generated loading/compilation, classloader retention and artifact stores/spools.

## Current scope

The current module registers lifecycle, duration and persistence signals. It is
useful to confirm that Gear4J is active, understand completed run/station latency
and verify that persistence buffers are not silently accumulating.

See the module README for the exact metric names:

```text
gear4jtest-micrometer/README.md
```

The built-in duration timers are recorded only when both start and end timestamps
are present and consistent. Missing or inverted timestamps are ignored rather than
emitting misleading zero or negative durations.

`PersistenceMetricsBinder` exposes active run buffers, buffered station logs,
the age of the oldest buffered log, scheduled/completed/failed flushes and
rejected appends. Cumulative failure and rejection counters are alert signals;
they are not used as permanent health state.

Internal runtime wiring can bind `EventMetricsBinder` to the run-local event
manager to expose best-effort in-memory counters: published/dispatched/dropped/
queued events, remaining event-queue capacity, and submitted/completed/dropped/
failed, pending and in-flight reactions. The stable consumer surface exposes
the process-wide aggregate instead of the concrete per-run runtime lifecycle.

`GeneratedLoadingMetricsBinder` exposes the end-to-end deadline, executor
saturation, compilation limits, cache results and every finite loading phase:
`artifact_read`, `translation`, `compilation`, `class_loading`, `construction`
and `injection`. Each phase has a functional timer, maximum-duration gauge and
failure counter. SHA-256 or size/metadata mismatches increment the tag-free
`gear4j.generated.loading.artifact.integrity.failures` counter.

`ClassLoaderMetricsBinder` exposes occupancy, aliases, bytecode weight,
evictions, rejections and protected-loader over-capacity state.
`ArtifactStoreMetricsBinder` exposes operation outcomes, bytes and cumulative
latency; `ArtifactSpoolMetricsBinder` exposes occupancy, quota rejections,
stale cleanup and cleanup failures.

Spring Boot auto-registers these binders only for a single candidate of each
supported type. It never chooses an arbitrary manager or store when several are
present.

## Remaining optional metric surface

A richer module should expose counters and timers for:

- pipeline outcomes: succeeded, failed, stopped, cancelled;
- station outcomes: succeeded, failed, skipped, stopped, cancelled;
- timeout and cancellation counts;
- persistence flush duration and latency distribution;
- parallel branch rejections and branch durations.
- persistence flush latency distributions;
- experimental-cache outcomes.

## Error categorization

Metrics do not expose raw exception messages or classes. Applications may
normalize domain/runtime failures into stable low-cardinality values such as:

```text
VALIDATION
OPERATOR_FAILURE
TIMEOUT
CANCELLATION
PERSISTENCE_FAILURE
LIFECYCLE_EXTENSION_FAILURE
UNKNOWN
```

Users can still implement their own `RuntimeExtension` if they need custom
metrics or application-specific tagging.

## Tag cardinality

The default policy is bounded: started counters have no tags and completed
counters/timers expose only the finite `status` value. Raw `pipeline.id`,
`operation.id` and `branch.id` values are not emitted by default.

Applications that need selected identifiers can use
`Gear4jMeterTagPolicy.allowlistedIdentifiers(...)`. Known identifiers retain
their value and every unknown value is aggregated as `other`, so the maximum
series count is reviewable before deployment. A custom policy remains available
for application-specific bounded dimensions.

`Gear4jMeterTagPolicy.legacyIdentifiers()` temporarily restores the old raw-ID
behavior for migration only. It is deprecated for removal because dynamically
generated identifiers can exhaust a metrics backend.

Infrastructure metrics do not consult that policy: their only tags are finite
framework-owned values named `phase`, `outcome`, `result` and `operation`.

## Recommended MVP dashboards

A first production dashboard should stay operational rather than business-level. Keep it low-cardinality and focus on
saturation, latency and drops:

| Panel | Metrics | Alert direction |
| --- | --- | --- |
| Run throughput | `gear4j.runs.started`, `gear4j.runs.completed` | completed much lower than started for a sustained period |
| Run latency | `gear4j.runs.duration` | p95/p99 above the application SLO |
| Station latency | `gear4j.stations.duration` | one station class starts dominating runtime |
| Persistence backlog | `gear4j.persistence.buffered.station.logs`, `gear4j.persistence.buffered.station.logs.oldest.age.seconds`, `gear4j.persistence.active.runs` | steadily increasing backlog or age |
| Persistence flush health | `gear4j.persistence.flushes.scheduled`, `gear4j.persistence.flushes.completed`, `gear4j.persistence.flushes.failed` | failed flushes increasing or completed no longer following scheduled |
| Persistence backpressure | `gear4j.persistence.appends.rejected` | any sustained non-zero value |
| Event queue pressure | `gear4j.events.queued`, `gear4j.events.queue.remaining.capacity`, `gear4j.events.dropped` | remaining capacity near zero or dropped events increasing |
| Reaction pressure | `gear4j.reactions.pending`, `gear4j.reactions.in.flight`, `gear4j.reactions.dropped`, `gear4j.reactions.failed` | pending grows, dropped/failed increases |
| Generated load saturation | `gear4j.generated.loading.executor.active`, `gear4j.generated.loading.executor.queued`, `gear4j.generated.loading.loads{outcome="rejected"}` | queue remains non-empty or rejections increase |
| Generated load deadlines | `gear4j.generated.loading.loads{outcome="timeout"}`, `gear4j.generated.loading.phase.duration{phase}` | timeouts increase or one phase dominates average duration |
| Artifact integrity | `gear4j.generated.loading.artifact.integrity.failures` | any increase requires investigation |
| Compilation pressure | `gear4j.generated.compilation.executor.active`, `gear4j.generated.compilation.executor.queued`, `gear4j.generated.compilations{outcome}` | queue/rejections/timeouts increase |
| Classloader pressure | `gear4j.generated.classloaders.cached`, `gear4j.generated.classloaders.bytecode.bytes`, `gear4j.generated.classloaders.evictions`, `gear4j.generated.classloaders.rejections` | capacity approached, churn or rejections increase |
| Artifact spool | `gear4j.artifacts.spool.bytes`, `gear4j.artifacts.spool.capacity.bytes`, `gear4j.artifacts.spool.quota.rejections`, `gear4j.artifacts.spool.cleanup.failures` | sustained high occupancy or any rejection/failure increase |

Do not add raw `pipeline.id`, `operation.id` or exception-message tags to a default dashboard. If an application needs
those dimensions, expose them through an explicit `Gear4jMeterTagPolicy` and review the expected cardinality first.
