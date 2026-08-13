# Future work and review notes

## Document control

| Field | Value |
| --- | --- |
| Status | Living backlog |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-08-13 |
| Review trigger | Before each release candidate and whenever a linked ADR changes status |

This file distinguishes delivered foundations, contract reviews and unscheduled backlog. A backlog entry is not a release
commitment unless a target version is stated explicitly.

Status values are `DELIVERED`, `REVIEW`, `BACKLOG` and `DEFERRED`.

## Delivered or constrained foundations

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Restricted GEL parser/evaluator and XML conditions | `DELIVERED` | 1.0 | [ADR 0008](../decisions/0008-gear-expression-language-is-security-boundary.md), [ADR 0018](../decisions/0018-gel-property-access-is-explicit.md), [ADR 0033](../decisions/0033-xml-operator-capabilities-are-mode-aware.md) | 2026-08-12 |
| Inline Java and Java class names restricted to explicit trusted XML mode | `DELIVERED` | 1.0 | [XML security boundary](../architecture/xml-security.md) | 2026-08-12 |
| Low-cardinality run/station outcomes and duration metrics | `DELIVERED` | 1.0 | [Micrometer observability](../architecture/micrometer-observability.md) | 2026-08-12 |
| Low-cardinality parallel-branch outcomes/rejections/duration and persistence-flush distributions | `DELIVERED` | 1.0 | [ADR 0039](../decisions/0039-runtime-latency-metrics-use-bounded-observations.md) | 2026-08-13 |

## Near-term review areas

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Logging strategy and signal-to-noise ratio | `REVIEW` | 1.0 review | — | 2026-08-12 |
| Runtime edge-case test coverage | `REVIEW` | Continuous | [Runtime guarantees](../runtime/runtime-guarantees.md) | 2026-08-12 |
| Extension ordering, failure impact and lifecycle contracts | `REVIEW` | 1.0 review | [Extensions](../architecture/extensions.md) | 2026-08-12 |
| Generic typing around station registries and generated code | `BACKLOG` | Unscheduled | — | 2026-08-12 |
| Packaging and module naming cleanup | `REVIEW` | 1.0 review | [Compatibility policy](../compatibility-policy.md) | 2026-08-12 |
| Skip semantics: condition skips are `SKIPPED`; fallback transformers only provide continuation output | `REVIEW` | 1.0 review | [Runtime error semantics](../runtime/error-semantics.md) | 2026-08-12 |
| Run timing ownership, including whether pre/post hooks are measured | `REVIEW` | 1.0 review | [Micrometer observability](../architecture/micrometer-observability.md) | 2026-08-12 |
| Exception conversion, propagation and fatal-error boundaries | `REVIEW` | 1.0 review | [Exception semantics](../architecture/exception-semantics.md) | 2026-08-12 |
| Container timeout, fail-fast, sequential failure and executor-ownership semantics | `REVIEW` | 1.0 review | [Core runtime](../architecture/core-runtime.md) | 2026-08-12 |
| Payload deep-cloning behavior and performance | `BACKLOG` | Post-1.0 | — | 2026-08-12 |

## Persistence and observability

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Adaptive terminal-record batching and custom failure/reporting policies | `BACKLOG` | Post-1.0 | [ADR 0015](../decisions/0015-terminal-station-persistence-batching.md) | 2026-08-12 |
| Context-propagation diagnostics and project-specific copier guidance | `BACKLOG` | Post-1.0 | [ADR 0016](../decisions/0016-nested-run-context-propagation-policy.md) | 2026-08-12 |
| Side-compute isolation for high-volume or latency-critical workloads | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| Event-dispatch bottleneck review at representative multi-run load | `BACKLOG` | Post-1.0 | [Events](../architecture/events.md) | 2026-08-12 |
| Local durable append before remote persistence flush | `BACKLOG` | Post-1.0 | [Durable events](../architecture/durable-events.md) | 2026-08-12 |
| Dedicated Gear4J datasource or connection pool | `BACKLOG` | Post-1.0 | [Dedicated persistence datasource](dedicated-persistence-datasource.md) | 2026-08-12 |

## Eventing

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Durable event subsystem, such as a JDBC outbox or local durable queue | `DEFERRED` | Post-1.0 | [ADR 0001](../decisions/0001-event-runtime-is-best-effort.md), [ADR 0002](../decisions/0002-event-transport-spi-is-a-future-extension.md) | 2026-08-12 |
| Explicit transport-envelope SPI | `BACKLOG` | Post-1.0 | [ADR 0002](../decisions/0002-event-transport-spi-is-a-future-extension.md) | 2026-08-12 |
| Retry and dead-letter strategy | `BACKLOG` | Post-1.0 | [Durable events](../architecture/durable-events.md) | 2026-08-12 |
| Idempotency guidance for reactions | `BACKLOG` | Post-1.0 | [Events](../architecture/events.md) | 2026-08-12 |

## Cancellation

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Kernel-driven execution control instead of scattered cancellation checks | `DEFERRED` | Post-1.0 | [Kernel-driven cancellation](kernel-driven-cancellation.md) | 2026-08-12 |
| Explicit `RUN` and `BRANCH` cancellation scopes | `BACKLOG` | Post-1.0 | [Kernel-driven cancellation](kernel-driven-cancellation.md) | 2026-08-12 |
| Soft-stop, interrupt, logical-abandon and isolated hard-cancel phases | `BACKLOG` | Post-1.0 | [Kernel-driven cancellation](kernel-driven-cancellation.md) | 2026-08-12 |
| User checkpoints only for blocking code outside framework-controlled boundaries | `REVIEW` | 1.0 review | [Runtime guarantees](../runtime/runtime-guarantees.md) | 2026-08-12 |

## XML and expressions

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Extend GEL beyond its delivered MVP with ordered comparisons, limited functions or station references | `BACKLOG` | Post-1.0 | [Gear4J expression language](gear-expression-language.md) | 2026-08-12 |

## External pipelines

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Dry-run execution and mock scenarios for BO validation | `BACKLOG` | Post-1.0 | [Dry-run and mock configuration](dry-run-and-mock-configuration.md) | 2026-08-12 |
| JSON map codec SPI for external persistence repositories | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| JSON translator module | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| Pipeline-reference dependency tracking | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| Alias invalidation events and cache staleness/TTL rules | `BACKLOG` | Post-1.0 | [External assembly lines](../architecture/external-assembly-lines.md) | 2026-08-12 |
| BO-facing metadata model | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| Iterator item-identity model | `REVIEW` | Post-1.0 | [Iterator item id model](item-id-model.md) | 2026-08-12 |

## Documentation

| Work item | Status | Target version | ADR / reference | Last verified |
| --- | --- | --- | --- | --- |
| Progressive first-use tutorial | `DELIVERED` | 1.0 | [Tutorial](../tutorial/getting-started.md), [ADR 0040](../decisions/0040-first-use-documentation-is-a-release-gated-consumer.md) | 2026-08-13 |
| Generated-code examples for XML | `BACKLOG` | Post-1.0 | [XML generation](../architecture/xml-generation.md) | 2026-08-12 |
| Runtime, event and external-compilation diagrams | `BACKLOG` | Post-1.0 | — | 2026-08-12 |
| Migration guide for finalized module/package names | `DELIVERED` | 1.0 | [1.0 migration](../migration/to-1.0.md), [compatibility policy](../compatibility-policy.md) | 2026-08-13 |
| JPMS descriptor review after public boundaries stabilize | `DEFERRED` | Post-1.0 | [Compatibility policy](../compatibility-policy.md) | 2026-08-12 |
