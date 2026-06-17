# 0009 — Phase 3 production readiness guardrails

## Status

Accepted.

## Context

After the build-time XML generation and runtime/persistence hardening passes,
Gear4J still needed a small production-readiness pass that does not introduce a
large new subsystem.

The major XML security topic is the Gear4J Expression Language (GEL). Inline
Java XML stays a trusted-source feature. GEL is the intended restricted
expression boundary for untrusted or semi-trusted pipeline definitions.

## Decision

Phase 3 adds pragmatic guardrails:

- a production readiness checklist in `docs/production-readiness.md`;
- explicit ADR for GEL as the restricted expression boundary;
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
- GEL exists as a deliberately small restricted evaluator. Future GEL work should
  remain additive and keep Java reflection/class access out of the untrusted path.
