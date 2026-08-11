# ADR 0023 - Phase 7 modular API and compatibility baseline

## Status

Accepted - 2026-07-13

## Context

Before 1.0, the provider-neutral external API pulled JDBC and Jackson, run inputs and generated definitions used raw
types, large runtime classes mixed validation/resolution responsibilities, and no automated N-1 compatibility gate
existed. Documentation also retained duplicate ADRs and an obsolete vendor media type.

## Decision

- Move external JDBC repositories, migrations, database artifact storage and its plugin to
  `gear4jtest-external-jdbc`; keep contracts, memory and filesystem stores in `gear4jtest-external-api`.
- Introduce `RunRequest<IN>` and `GeneratedAssemblyLine<IN, OUT>` before freezing the 1.0 baseline.
- Extract baseline schema validation and event subscription resolution; retain the already separated XML
  validation/parsing/rendering pipeline rather than rewriting it.
- Aggregate event drops, reaction drops, shared-dispatch rejection and queue-to-dispatch latency process-wide and bind
  tag-free metrics automatically in the Spring Boot starter.
- Upgrade the wrapper to Gradle 9.6.1, verify its distribution checksum and exercise the configuration cache in CI.
- Enforce Java source/binary N-1 checks with Japicmp after 1.0.0 and define compatibility for XML, DB migrations,
  properties, metrics and health semantics.
- Require exactly one stability marker on every production package in every published Java library module.
- Treat the Gradle extension DSL, task property names and both published plugin ids as 1.x contracts. Check the
  implementation artifact with Japicmp and execute versioned consumer fixtures with TestKit.

## Consequences

Pre-1.0 consumers of JDBC classes must add the new artifact and update package imports once. This intentional move is
completed before the stable baseline so later 1.x releases can enforce compatibility. The external API no longer pulls a
database stack for memory/filesystem users. Internal and experimental packages remain outside the stable promise. JPMS
descriptors remain a separate final roadmap item; automatic module names are preserved in the meantime.
The Gradle plugin implementation and task types remain internal wiring; only the extension DSL and named task
properties are frozen for 1.x consumers.
