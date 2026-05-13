# Dry-run and mock configuration

## Status

Future direction. Not implemented yet.

This note keeps the intent behind the former commented dry-run/mock-configuration experiments. The old code should not be
kept as dead code, but the feature remains important for Gear4J, especially for external pipeline authoring and future BO
workflows.

## Why this matters

Gear4J should eventually let users validate and exercise a pipeline without necessarily executing all real side effects.
This is useful for:

- testing a pipeline definition from a BO before publishing it;
- validating XML/JSON translated pipelines after compilation and dependency injection;
- demonstrating the expected flow of a pipeline with deterministic inputs and outputs;
- checking branch, skip, error and fallback behavior without calling real external systems;
- onboarding users by showing what a pipeline would do before it is run for real.

## Core idea

A dry-run should be an explicit execution mode, not a hidden test shortcut.

A possible future model is:

- `RunMode.NORMAL`: execute the pipeline normally;
- `RunMode.DRY_RUN`: execute through the runtime while replacing selected side-effecting components with configured
  behavior;
- `MockConfiguration`: describes which operators, processors, services or stations are mocked and what they return or
  throw.

The exact API should be redesigned before implementation. The previous idea is useful as product intent, but should not
be treated as the final design.

## Mock configuration scope

A future `MockConfiguration` could support several levels of mocking:

- mock an operator or processor by class;
- mock a station by id;
- mock injected services by injection name or type;
- return fixed values;
- throw configured exceptions;
- simulate skips, failures, delays or fallback paths;
- provide scenario names for BO-driven tests.

The API should make it clear whether a mock is applied by station id, component type, injection name or pipeline
reference. Ambiguous matching should fail fast.

## Runtime semantics to define

Before implementing dry-run, define these semantics explicitly:

- whether persistence is disabled, replaced by in-memory traces, or marked as dry-run;
- whether events are emitted, suppressed, or emitted with a dry-run marker;
- whether side-compute reactions run in dry-run mode;
- how failures are reported in `ExecutionResult`;
- whether injected dependencies are mocked globally or per pipeline/run;
- how nested and inline pipeline calls inherit or override dry-run configuration;
- whether payload cloning behaves exactly like normal execution;
- how to prevent accidental calls to real external systems.

## Recommended architecture

Do not implement dry-run through AspectJ or implicit method interception.

Prefer explicit runtime integration points:

- `RunRequest` or a dedicated execution options object for selecting dry-run mode;
- a run-scoped dry-run/mock configuration accessible through `ExecutionServices` or a dedicated runtime capability;
- station wrappers or strategy-level hooks for replacing station/component execution;
- explicit trace metadata so dry-run executions are visibly different from real executions;
- module-level integration for external pipeline definitions so the BO can submit scenarios.

The design should preserve Gear4J's current preference for explicit, testable runtime behavior over hidden magic.

## BO and external-pipeline use cases

The BO should eventually be able to:

1. load or edit an external pipeline definition;
2. provide sample input and a named mock scenario;
3. compile/load the pipeline;
4. execute it in dry-run mode;
5. display the resulting station trace, skipped branches, mocked outputs and failures;
6. publish the pipeline only after the scenario behaves as expected.

This is especially useful for XML/JSON pipelines where the author may not have direct Java-level tests.

## Non-goals for the first version

The first dry-run implementation should not try to be a complete simulator of every side effect.

Non-goals:

- exactly reproducing all production infrastructure behavior;
- making side-effecting user code safe without explicit mocks;
- durable eventing or replay;
- replacing normal unit/integration tests;
- hiding errors caused by missing mocks.

If a dry-run reaches a side-effecting component without a configured mock, the safest default may be to fail fast unless
the user explicitly allows real execution for that component.

## Open questions

- Should dry-run be configured on `RunRequest`, `AssemblyLine.Configuration`, or both?
- Should `MockConfiguration` live in core, external-api, or a dedicated test/simulation module?
- Should mock scenarios be serializable for BO usage?
- Should dry-run traces be persisted, and if yes, should they live in the same tables as real runs?
- How should dry-run interact with pipeline calls, especially inline calls that share parent context?
- What is the minimal MVP that proves the feature without over-designing it?
