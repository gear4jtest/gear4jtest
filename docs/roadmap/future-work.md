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
- Run timing ownership: decide exactly which component sets begin/end times and whether pre/post hooks are included in measured orchestration time.
- Exception policy: define where exceptions are caught, converted to `ExecutionResult`, rethrown or treated as fatal.
- Container behavior: timeouts while waiting for futures, fail-fast semantics, sequential branch failure behavior and executor ownership.
- Payload deep cloning behavior and performance.

## Persistence and observability

Potential future work:

- Improve persistence flushing strategy.
- Avoid a single bottleneck thread for all pipelines if that becomes a real limit.
- Consider local durable append before remote DB flush for stronger crash behavior.
- Add Micrometer metrics once the runtime surface is stable.

## Eventing

Potential future work:

- Durable event subsystem separate from `EventManager`.
- JDBC outbox or local durable queue.
- Explicit transport envelope SPI.
- Retry and dead-letter strategy.
- Idempotency guidance for reactions.

## Cancellation

Potential future work:

- Add cooperative cancellation visible to user operators/processors.
- Define a clear cancellation token API.
- Document which runtime operations are interrupt-driven, status-driven or cooperative.

## External pipelines

Potential future work:

- JSON translator module.
- Better dependency tracking for pipeline references.
- Alias invalidation events.
- Cache staleness and TTL rules.
- BO-facing metadata model.

## Documentation

Potential future work:

- Add a first tutorial once APIs stabilize.
- Add generated-code examples for XML.
- Add diagrams for runner chain, event runtime and external pipeline compilation.
- Add a migration guide when module/package names are finalized.
