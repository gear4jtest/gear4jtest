# 0040 - First-use documentation is a release-gated consumer

## Status

Accepted - 2026-08-13

## Context

Gear4J has detailed module and architecture references, but a new consumer still has to assemble the first public
pipeline from scattered examples. Ordinary Markdown snippets can preserve obsolete engine imports or builder calls
after an API refactor even when all production tests remain green. A tutorial-only source set compiled against project
dependencies would also miss incorrect scopes in published POMs.

## Decision

- Maintain one progressive first-use tutorial based on the public `AssemblyLine`, `RunRequest`,
  `AssemblyLineExecutor`, `ExecutionResult`, `Operator` and `ResourceFactory` contracts.
- Keep its complete Java source in the autonomous staged-artifact consumer under `config/consumer-smoke`.
- Execute the example from `ConsumerSmoke`, including its expected terminal outcome and value, instead of treating it as
  compilation-only sample code.
- Compile it through the same consumer that resolves Gear4J modules and the XML Gradle plugin exclusively from the
  staged Maven repository.
- Keep the migration guide evidence-based: list removed promoted types and supported replacements, public facade rules,
  module coordinates and operational review points already enforced or documented by the repository.
- Link tutorial excerpts to the complete source. A change to a copied excerpt must update the executable source in the
  same change.

## Consequences

A release cannot publish an API or POM-scope change that prevents the documented first pipeline from compiling and
running. The smoke is intentionally small and does not replace module tests, database dialect qualification or Spring
context tests. Markdown excerpts are still copies, so review and link validation remain necessary; the staged consumer
is the behavioral source of truth.
