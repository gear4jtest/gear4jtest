# 0007 — Phase 3 long-term operational capabilities

## Status

Accepted.

## Context

The first phase 3 pass added CI, release automation and Maven Central staging.
The long-term roadmap also requires runtime and operational capabilities that
must remain optional and incremental:

- versioned JDBC schema migrations per dialect;
- Spring Boot starter with validated properties;
- optional Micrometer observability;
- dependency locking, dependency verification and SCA in CI;
- safe handling of XML definitions from untrusted sources;
- a durable event/outbox SPI separated from the in-memory event runtime.

## Decision

Gear4J keeps the core runtime lightweight and adds optional modules/SPIs for
operational concerns.

### JDBC migrations

Gear4J JDBC components use a small internal versioned migrator. It records
applied migrations in `gear4j_schema_history` using `(module_id, version)` as the
primary key. The migrator is explicitly dialect-driven and never auto-detects the
database provider.

Applications that already own schema evolution through Flyway/Liquibase can keep
`autoCreateTables=false` and vendor Gear4J SQL into their own migration sequence.
Directly pointing Flyway at Gear4J internal `V1__...` resources is not the
recommended default because those versions may collide with the host
application's migration numbering.

### Spring Boot starter

Boot integration lives in `gear4jtest-spring-boot-starter`. It imports the plain
Spring configuration and exposes validated `gear4j.*` properties. JDBC
persistence is opt-in and requires an explicit dialect.

### Micrometer

Micrometer support lives in `gear4jtest-micrometer`. The core module has no
Micrometer dependency. The Boot starter wires the Micrometer extension only when
a `MeterRegistry` is available and metrics are enabled.

The current module is an initial low-cardinality integration, not the final
observability story. Richer metrics such as failed stations, failed pipelines,
durations, timeouts, cancellations and error categories should be added with an
explicit tag policy to avoid accidental high-cardinality series. See `docs/architecture/micrometer-observability.md`.

### Supply chain

Dependency locking is enabled for all projects. Gradle dependency verification
metadata must be generated from a trusted environment and committed. CI runs
OWASP Dependency-Check as SCA.

### XML non trusted

Generated Java remains supported for trusted XML. Untrusted XML must use an
explicit `XmlJavaSourcePolicy`, for example `forbidInlineJava()`. Gear4J does not
claim to sandbox arbitrary inline Java snippets.

The preferred long-term solution for BO-authored or untrusted definitions is a
Gear4J expression language with a controlled AST and whitelisted functions.

### Durable events

The existing event runtime remains best-effort and in-memory. Durable delivery is
represented by a separate outbox-style SPI under `event.durable`, based on
persisted `EventEnvelope` objects and at-least-once dispatch. It is not wired
into the in-process runtime by default.

The current phase provides contracts and a generic dispatcher. A production JDBC
outbox store, retry policy, dead-letter policy and automatic wiring remain future
work.

## Consequences

- Operational features are available without turning the core engine into a
  framework-heavy runtime.
- The JDBC schema can evolve through additive migrations.
- Boot users get a simple validated configuration surface.
- Maven Central artifacts remain modular: users only depend on Micrometer or Boot
  when needed.
- Untrusted XML becomes an explicit security boundary.
- Durable event delivery can evolve independently from best-effort in-process
  reactions.
