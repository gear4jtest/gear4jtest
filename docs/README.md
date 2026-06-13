# Gear4J documentation

This directory contains longer-lived design documentation.

Use module README files for module-specific usage. Use this directory for architectural concepts, decisions and
future-direction notes that should survive individual refactors.

## Structure

- `architecture/`: durable explanations of runtime, extension, event, external-pipeline, XML-generation, JDBC migration, Boot and observability architecture.
- `decisions/`: decision records and future-direction notes.
- `roadmap/`: known work items, review notes and non-MVP ideas, including kernel-driven cancellation and Gear4J expression language.
- `runtime/`: implemented runtime semantics and execution contracts.
- `security/`: supply-chain and runtime security guidance.
- `releasing.md`: release workflow and Maven Central publication checklist.

## Status vocabulary

Use these status markers consistently:

- `Implemented`: behavior exists in code.
- `Partially implemented`: some pieces exist, but the full design is not complete.
- `Future direction`: desirable design direction, not currently implemented.
- `Not implemented`: explicitly not present today.
- `Non-goal`: intentionally out of scope for the current subsystem.

Do not describe future-direction ideas as current behavior.

- [Production readiness checklist](production-readiness.md)
