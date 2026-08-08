# Future work and review notes

## Status

Living note.

This file collects known review topics and future ideas. Items here are not necessarily implemented.

## Near-term review areas

- Logging strategy and signal-to-noise ratio.
- Test coverage around runtime edge cases.
- Extension strategy, ordering and failure impact.
- Generic typing around station registries and generated code.
- Packaging and module naming cleanup.
- Skip semantics: condition-triggered station skips are represented as `SKIPPED`; fallback transformers only provide continuation output.
- Run timing ownership: decide exactly which component sets begin/end times and whether pre/post hooks are included in
  measured orchestration time.
- Exception policy: define where exceptions are caught, converted to `ExecutionResult`, rethrown or treated as fatal.
- Container behavior: timeouts while waiting for futures, fail-fast semantics, sequential branch failure behavior and
  executor ownership.
- Payload deep cloning behavior and performance.

## Persistence and observability

Potential future work:

- Consider advanced persistence tuning after ADR 0015: adaptive batch sizing, explicit metrics for core terminal-record buffers, and custom failure/reporting policies for batched terminal snapshots.
- Consider advanced context propagation diagnostics after ADR 0016, such as metrics for omitted keys or reusable project-specific context copiers.
- Review side-compute execution isolation if side-compute becomes high-volume or latency-critical. The current model waits synchronously from the station thread for work completed by the async event/reaction infrastructure.
- Avoid a single bottleneck thread for all pipelines if that becomes a real limit.
- Consider local durable append before remote DB flush for stronger crash behavior.
- Add parallel-branch and persistence-flush latency distributions only after defining stable lifecycle hooks that do not couple Micrometer to core. See [Micrometer observability](../architecture/micrometer-observability.md).
- Consider a dedicated Gear4J `DataSource` / connection pool for persistence isolation. See [dedicated persistence datasource](dedicated-persistence-datasource.md).

## Eventing

Potential future work:

- Durable event subsystem separate from `EventManager`; SPI exists, JDBC outbox implementation remains future work.
- JDBC outbox or local durable queue.
- Explicit transport envelope SPI.
- Retry and dead-letter strategy.
- Idempotency guidance for reactions.

## Cancellation

Potential future work:

- Move from scattered cancellation checks to a kernel-driven execution control model. See [kernel-driven cancellation](kernel-driven-cancellation.md).
- Define `RUN` vs `BRANCH` cancellation scopes.
- Define cancellation policy phases: soft stop, interrupt, logical abandon and hard cancellation for isolated workers.
- Keep user-facing checkpoints only for long-running or blocking user code that cannot be controlled by framework boundaries.

## XML and expressions

Potential future work:

- Introduce a safe Gear4J expression language for BO-authored and untrusted XML definitions. See [Gear4J expression language](gear-expression-language.md).
- Keep inline Java as a trusted developer/admin feature only.

## External pipelines

Potential future work:

- Dry-run execution and mock scenarios for BO-driven pipeline validation. See [dry-run and mock configuration](dry-run-and-mock-configuration.md).
- Introduce a small JSON map codec abstraction for external persistence repositories. The optional
  `gear4jtest-external-jdbc` module currently uses Jackson directly; a future extraction could let repositories depend on
  a `JsonMapCodec` contract and move the Jackson implementation to a narrower integration module.
- JSON translator module.
- Better dependency tracking for pipeline references.
- Alias invalidation events.
- Cache staleness and TTL rules.
- BO-facing metadata model.
- Revisit iterator item identity semantics. See [iterator item id model](item-id-model.md).

## Documentation

Potential future work:

- Add a first tutorial once APIs stabilize.
- Add generated-code examples for XML.
- Add diagrams for runner chain, event runtime and external pipeline compilation.
- Add a migration guide when module/package names are finalized.
- Revisit JPMS descriptors before 1.0 once public API/SPI boundaries and advanced extension points are stable.
