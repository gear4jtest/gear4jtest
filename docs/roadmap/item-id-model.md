# Iterator item id model

## Document control

| Field | Value |
| --- | --- |
| Status | Review topic; targeted propagation fix delivered, broader model open |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-08-12 |
| Target version | Post-1.0; unscheduled |

## Context

Iterator stations can derive an item id for each iterated element. This id is useful for station logs, events, debugging,
BO traceability and partial-failure analysis.

The current runtime stores the active item id in the global execution context while an iterator child chain runs. This
allows child station traces to capture the current item id, but it is a fragile mechanism because it depends on scoped
mutation and restoration.

## Current short-term behavior

For each iterator element:

1. resolve the item id using the configured resolver, or a deterministic default;
2. store it as the current item id before executing the child chain;
3. restore the previous item id in a `finally` block.

This is intentionally compatible with nested iterators.

## Open questions

- Should item identity be represented by a scoped object rather than a mutable thread-local string?
- Should events capture the item id at publication time to avoid accidental async context leakage?
- Should nested iterators expose a stack/path of item ids rather than only the current item?
- Should item ids be validated for stability/emptiness the same way branch ids are?
- How should item ids appear in BO run details and replay/dry-run scenarios?

## Non-goal

Do not redesign the full item identity model as part of unrelated persistence or iterator bug fixes.
