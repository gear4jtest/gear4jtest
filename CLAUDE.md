# Claude instructions for Gear4J

Read `AGENTS.md` first. It is the repository-wide source of truth for agent instructions.

Also check module-level `AGENTS.md` files before editing files inside:

- `gear4jtest-core/`
- `gear4jtest-external-api/`
- `gear4jtest-xml/`

For architectural context, prefer:

- `docs/architecture/*.md`
- `docs/decisions/*.md`
- module README files

When producing changes:

- keep patches minimal and reviewable;
- include tests for behavior changes;
- do not present future-direction notes as implemented behavior;
- report which Gradle commands were run;
- explicitly mention commands that could not be run and why.
