# Gear4J documentation

This directory contains longer-lived design documentation.

Use module README files for module-specific usage. Use this directory for architectural concepts, decisions and
future-direction notes that should survive individual refactors.

## Structure

- `architecture/`: durable explanations of runtime, extension, event, external-pipeline, XML-generation, JDBC migration, Boot and observability architecture.
- `audit/`: finding closure matrices and explicitly deferred remediation work.
- `decisions/`: decision records and future-direction notes.
- `migration/`: source and operational upgrade guidance for stable public boundaries.
- `roadmap/`: known work items, review notes and non-MVP ideas, including kernel-driven cancellation and Gear4J expression language.
- `runtime/`: implemented runtime semantics and execution contracts.
- `security/`: supply-chain and runtime security guidance.
- `tutorial/`: progressive, release-gated first-use guidance.
- `releasing.md`: release workflow and Maven Central publication checklist.
- `performance.md`: JMH scenarios, versioned budgets, coverage ratchets and database-matrix execution.

## Status vocabulary

Use these status markers consistently:

- `Implemented`: behavior exists in code.
- `Partially implemented`: some pieces exist, but the full design is not complete.
- `Future direction`: desirable design direction, not currently implemented.
- `Not implemented`: explicitly not present today.
- `Non-goal`: intentionally out of scope for the current subsystem.

Do not describe future-direction ideas as current behavior.

- [Production readiness checklist](production-readiness.md)

- [Build and run a first Gear4J pipeline](tutorial/getting-started.md)
- [Migrate a pre-1.0 application to the Gear4J 1.0 surface](migration/to-1.0.md)
- [Technical-audit remediation roadmap](roadmap/audit-remediation-2026-09-04.md)
- [Audit remediation phase 2: runtime and persistence hardening](audit/remediation-2026-09-04-phase-2-runtime-persistence-hardening.md)
- [Audit remediation phase 3: API and maintainability](audit/remediation-2026-09-04-phase-3-api-maintainability.md)

- [API, SPI and internal contracts](architecture/api-contracts.md)
- [Runtime logging strategy](architecture/logging.md)
- [Final 1.0 runtime contract review](audit/final-runtime-contract-review-phase-20-2026-08-13.md)
- [Runtime guarantees and non-guarantees](runtime/runtime-guarantees.md)
