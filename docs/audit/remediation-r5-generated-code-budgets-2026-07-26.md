# R5 — Hard generated-code and classloader budgets

R5 closes audit finding F-04.

## Behavioral change

Before 1.0, every XML translation and generated compilation now applies finite
hard limits. Definitions that exceed the defaults are rejected instead of being
compiled or loaded outside the cache budget.

Defaults:

- 1,000 total XML operations;
- 256 XML dependencies;
- XML operation nesting depth 32;
- 4 MiB generated UTF-8 source;
- 8 MiB cumulative bytecode per compilation;
- 64 MiB cumulative classloader bytecode weight.

This affects trusted XML as well as restricted XML. Increase a specific finite
limit only after aligning it with the application's heap, metaspace, publication
concurrency and rollback-window budget.

## Verification

Focused tests cover source rejection before compiler dispatch, output rejection
before return, weighted LRU eviction, rejection when aliases prevent eviction,
release of defined class bytes, XML operation/dependency/depth limits,
generated-source size and Gradle task inputs.
