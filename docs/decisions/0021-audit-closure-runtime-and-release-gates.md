# 0021 - Audit closure runtime and release gates

## Status

Accepted.

## Context

The audit remediation left three cross-cutting guarantees implicit: concurrent external lookups could compile the same
artifact more than once, mutable values in a top-level context had no configurable isolation boundary, and the release
build did not prove that staged artifacts or the Gradle plugin were consumable outside the multi-project build.

## Decision

- Generated assembly-line loading is single-flight per immutable loader id. Every caller observes the same generated
  instance, failures are shared with current waiters and failed flights are removed for retry.
- Only a latest RUN lookup may update the local `latest` alias. Explicit-version lookups never do. A publication
  increments a local invalidation generation so an older compilation cannot restore a stale alias afterward.
- Top-level runs retain shallow-copy behavior by default for compatibility. Applications that place mutable values in
  defaults or request context configure `AssemblyLineEngine.Builder.initialRunContextPolicy(...)` to copy or filter
  those values before execution.
- `releaseCheck` stages every library, the Gradle plugin and both plugin markers, then compiles and executes the
  autonomous `config/consumer-smoke` project exclusively against that repository.
- JReleaser applies Maven Central validation and signing to non-snapshot releases. Secrets remain external to source.
- Dependency locks and verification metadata remain optional during the MVP period; their absence can be made fatal
  explicitly with `gear4j.enforceSupplyChain=true`.

## Consequences

Concurrent loading no longer creates divergent generated class identities, while transient compiler failures remain
retryable. Context isolation cost is paid only by applications that opt into a value-copy policy. Release metadata and
plugin markers are now exercised as a consumer sees them rather than inferred from project dependencies.

The generation guard is local to one manager and does not claim distributed cache coherence. A consumer-provided value
copy function must understand the mutable types it accepts. Supply-chain enforcement, performance budgets, API
compatibility tooling and module extraction remain explicit post-MVP work.
