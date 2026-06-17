# 0009 — Phase 3 production readiness guardrails

## Status

Accepted.

## Context

After the build-time XML generation and runtime/persistence hardening passes,
Gear4J still needed a small production-readiness pass that does not introduce a
large new subsystem.

The major long-term security topic remains GEL, the future Gear4J Expression
Language. GEL is not implemented in this pass because it is a language/runtime
design of its own. The decision is nevertheless explicit: inline Java XML stays a
trusted-source feature and GEL is the intended security boundary for untrusted
pipeline definitions.

## Decision

Phase 3 adds pragmatic guardrails:

- a production readiness checklist in `docs/production-readiness.md`;
- explicit ADR for GEL as the future security boundary;
- bounded artifact read APIs so applications can reject oversized artifacts
  before loading them fully in memory;
- an optional `maxArtifactSizeBytes` constructor path in `AssemblyLineManager`;
- optional Spring Boot Actuator health integration for JDBC persistence;
- docs for the Actuator health bean and artifact size policy.

## Consequences

- Runtime XML compilation remains trusted-only.
- Composite artifact writes now preserve the bounded streaming path by spooling
  multi-store writes through temporary files instead of forcing the full artifact
  into heap memory. Individual stores may still choose their own storage strategy.
- Spring Boot applications with Actuator can diagnose persistence buffer/flush
  state through the standard health infrastructure.
- The project still avoids implementing the full GEL interpreter until its syntax,
  AST and type model are designed deliberately.
