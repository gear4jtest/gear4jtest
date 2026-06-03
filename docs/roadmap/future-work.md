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
- Skip semantics: decide whether skipped stations should be marked success, skipped, or another explicit status.
- Run timing ownership: decide exactly which component sets begin/end times and whether pre/post hooks are included in
  measured orchestration time.
- Exception policy: define where exceptions are caught, converted to `ExecutionResult`, rethrown or treated as fatal.
- Container behavior: timeouts while waiting for futures, fail-fast semantics, sequential branch failure behavior and
  executor ownership.
- Payload deep cloning behavior and performance.

## Persistence and observability

Potential future work:

- Improve persistence flushing strategy.
- Avoid a single bottleneck thread for all pipelines if that becomes a real limit.
- Consider local durable append before remote DB flush for stronger crash behavior.
- Enrich the Micrometer module with durations, failures, cancellations, event stats and an explicit tag policy. See [Micrometer observability](../architecture/micrometer-observability.md).
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
- Introduce a small JSON map codec abstraction for external persistence repositories. The MVP currently lets
  `OperationChainConfigRepositoryJdbc` use Jackson directly inside `external-api`, but a future extraction should let the
  repository depend on a `JsonMapCodec` contract and move the Jackson implementation to a dedicated integration module.
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
